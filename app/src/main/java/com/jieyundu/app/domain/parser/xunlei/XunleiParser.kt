// 文件：XunleiParser.kt
// 职责：迅雷云盘分享链接解析与分享目录展开（阶段 1A：只读，不含转存与下载）
// 依赖：XunleiApi、XunleiConfig、XunleiModels、LinkExtractor、FileInfo、ParseResult、ShareBrowser、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.xunlei

import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.ParseResult
import com.jieyundu.app.domain.parser.NetdiskParser
import com.jieyundu.app.domain.parser.ShareBrowser
import com.jieyundu.app.domain.util.LinkExtractor
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import timber.log.Timber

/**
 * 迅雷云盘解析器（【JYD-XUNLEI-P1A-2026-10-08】，阶段 1A：分享解析与浏览）。
 *
 * 已实现（依《抓包事实.md》§4 / §6.3 / §11.4）：
 * - 分享解析 `GET drive/v1/share?share_id=&pass_code=&limit=&page_token=&thumbnail_size=`：
 *   取 `title / files[] / pass_code_token / next_page_token`，
 *   并按 `share_status`（`PASS_CODE_EMPTY` / `PASS_CODE_NEED` / `PASS_CODE_ERROR`）分流为
 *   成功 / 需要提取码 / 提取码错误；
 * - 分享子目录 `GET drive/v1/share/detail?share_id=&parent_id=&pass_code_token=&limit=&page_token=`；
 * - **游客模式**：分享接口「Bearer 或游客匿名（不写 Authorization）」（§11.4 #10），
 *   因此未登录也能解析分享——鉴权头由 `data/remote/XunleiAuthInterceptor` 按有无令牌决定。
 *
 * 尚未实现（阶段 1B / 2 / 3，均需先定登录方式）：
 * - WebView 登录 → access_token（**事实文档未给出该链路**，见 CHANGELOG 的待决项）；
 * - 转存 `share/restore` + 任务轮询 + 取链 `files/{id}`（阶段 2）；
 * - 个人网盘列表 / 删除 / 容量（阶段 3）。
 */
@Singleton
class XunleiParser @Inject constructor(
    private val api: XunleiApi
) : NetdiskParser, ShareBrowser {

    override val type: NetdiskType = NetdiskType.XUNLEI

    /**
     * 迅雷分享链接判定：交由统一的域名正则。
     *
     * @param url 分享链接。
     * @return 是否属于迅雷云盘。
     */
    override fun match(url: String): Boolean =
        LinkExtractor.detectType(url) == NetdiskType.XUNLEI

    /**
     * 解析迅雷云盘分享链接（含提取码分流）。
     *
     * @param url 分享链接。
     * @param pwd 提取码；无码分享传 null（此时请求不带 `pass_code`）。
     * @return 解析结果：成功 / 需要提取码 / 失败。
     */
    override suspend fun parse(url: String, pwd: String?): ParseResult {
        val shareId = LinkExtractor.extractShareId(url)
        if (shareId.isBlank()) {
            return ParseResult.Error(type, CODE_INVALID_LINK, CODE_INVALID_LINK)
        }
        return withContext(Dispatchers.IO) {
            try {
                val response = api.share(
                    shareId = shareId,
                    passCode = pwd?.takeIf { value -> value.isNotBlank() }
                )
                when (response.share_status) {
                    // 需要提取码：交 UI 弹输入框（不当作错误，与夸克 / UC / 百度同语义）。
                    XunleiConfig.SHARE_STATUS_PASS_CODE_NEED ->
                        return@withContext ParseResult.NeedPassword(type)
                    // 提取码错误：透传服务端描述（若有），否则回落到本模块错误码。
                    XunleiConfig.SHARE_STATUS_PASS_CODE_ERROR -> {
                        Timber.w("XunleiParser share rejected: pass code error")
                        return@withContext ParseResult.Error(
                            type,
                            CODE_WRONG_PASSWORD,
                            serverDetail(response)
                        )
                    }
                    // PASS_CODE_EMPTY 或未下发 share_status：按成功处理（files 可能为空）。
                    else -> Unit
                }
                ParseResult.Success(
                    netdiskType = type,
                    shareTitle = response.title.orEmpty(),
                    files = response.files.map { entry -> entry.toFileInfo() },
                    pwdId = shareId,
                    // 迅雷的「分享令牌」是提取码令牌（pass_code_token），展开子目录时回传。
                    stoken = response.pass_code_token.orEmpty()
                )
            } catch (cancellation: CancellationException) {
                // C3：协程取消必须原样抛出，不得吞掉
                throw cancellation
            } catch (io: IOException) {
                Timber.e(io, "XunleiParser parse failed: network error")
                ParseResult.Error(type, CODE_NETWORK, CODE_NETWORK)
            } catch (serialization: SerializationException) {
                Timber.e(serialization, "XunleiParser parse failed: unexpected response body")
                ParseResult.Error(type, CODE_PROTOCOL, CODE_PROTOCOL)
            }
        }
    }

    /**
     * 展开分享内的某个目录（`share/detail`）。
     *
     * 说明：
     * - `parent_id` 取目录条目的 `id`（见 [XunleiShareFile.toFileInfo]）；
     * - 防御分支：若调用方传入根目录（空 / `0`），则改用**分享解析接口**重新列根目录
     *   （`share/detail` 的根 `parent_id` 无抓包依据，不臆造取值）；
     * - 失败返回空列表（UI 侧按「空目录 / 加载失败」提示，不崩溃）。
     *
     * @param pwdId 分享 ID。
     * @param stoken 提取码令牌（`pass_code_token`）。
     * @param pdirFid 目标目录 id。
     * @return 该目录下的条目列表。
     */
    override suspend fun listChildren(
        pwdId: String,
        stoken: String,
        pdirFid: String
    ): List<FileInfo> = withContext(Dispatchers.IO) {
        val isRoot = pdirFid.isBlank() || pdirFid == ROOT_PARENT_ID
        try {
            if (isRoot) {
                // 根目录：用已证实的分享接口列（不带提取码；无码分享即等价）。
                api.share(shareId = pwdId, passCode = null).files.map { entry -> entry.toFileInfo() }
            } else {
                api.shareDetail(
                    shareId = pwdId,
                    parentId = pdirFid,
                    passCodeToken = stoken.takeIf { value -> value.isNotBlank() }
                ).files.map { entry -> entry.toFileInfo() }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Timber.e(exception, "XunleiParser listChildren failed, dir=%s", pdirFid)
            emptyList()
        }
    }

    /**
     * 组装失败详情：优先服务端描述，其次服务端错误标识。
     *
     * ⚠️ `error` / `error_description` 两个字段名**未在事实文档中证实**（§11.4 #10 只列了
     * `share_status`）；此处按「有就透传」的宽容口径读取，字段名待抓包确认。
     *
     * @param response 分享响应。
     * @return 详情文本。
     */
    private fun serverDetail(response: XunleiShareResponse): String =
        response.error_description?.takeIf { value -> value.isNotBlank() }
            ?: response.error?.takeIf { value -> value.isNotBlank() }
            ?: response.share_status.orEmpty().ifBlank { CODE_WRONG_PASSWORD }

    /**
     * 把迅雷条目映射为统一模型。
     *
     * 说明：`fid` 用条目 `id`（目录展开与后续转存都用它）；文件大小字段名待抓包确认，
     * 因此 [FileInfo.fileSize] 暂为模型默认值（见 [XunleiShareFile] 的 TODO）。
     *
     * @return 统一文件条目。
     */
    private fun XunleiShareFile.toFileInfo(): FileInfo = FileInfo(
        fid = id,
        fileName = name,
        fileSize = size,
        isDirectory = isDirectory,
        // 迅雷取链在阶段 2（files/{id} → data.links），本批不产生直链。
        downloadUrl = null
    )

    private companion object {
        /** 分享根目录占位（UI 侧约定：根为 `0`）。 */
        const val ROOT_PARENT_ID = "0"

        /** 链接不合法。 */
        const val CODE_INVALID_LINK = "XUNLEI_INVALID_LINK"

        /** 提取码错误。 */
        const val CODE_WRONG_PASSWORD = "XUNLEI_WRONG_PASSWORD"

        /** 网络异常。 */
        const val CODE_NETWORK = "XUNLEI_NETWORK_ERROR"

        /** 响应结构异常。 */
        const val CODE_PROTOCOL = "XUNLEI_PROTOCOL_ERROR"
    }
}
