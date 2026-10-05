// 文件：BaiduParser.kt
// 职责：百度网盘分享链接解析与分享目录展开（B3-1）
// 依赖：BaiduApi、LinkExtractor、FileInfo、ParseResult、ShareBrowser、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.baidu

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
 * 百度网盘解析器（《要求.md》阶段 B3；本批为 **B3-1：分享解析与浏览**）。
 *
 * 已实现（依《抓包事实.md》§3 / §6.2 / §11.3）：
 * - 分享短码归一化（`LinkExtractor` 负责去掉百度 `/s/1xxxx` 的**前导 `1`**）；
 * - 提取码校验 `share/verify` → `randsk`（当 `sekey` 用；其 `Set-Cookie: BDCLND` 由网络层采集）；
 * - 分享文件列表 `xpan/share?method=list`（顶层 `root=1`）；
 * - 分享目录逐级展开（子目录 `root=0` + `dir=<完整路径>`，路径即目录 id）。
 *
 * 尚未实现（**B3-2**，均需转存链路，见《抓包事实.md》§11.3 #6 / #7）：
 * 转存 `share/transfer` → 高速直链 `locatedownload` → 下载 → 清理转存副本。
 * 故本批解析出的文件**尚未带直链**（[FileInfo.downloadUrl] 为 null），首页下载按钮对百度
 * 暂不可用；这是**有意为之的分批**，不是遗漏。
 *
 * 关于「未带提取码时的失败归类」：百度「需要提取码 / 提取码错误」的 `errno` 取值
 * 在现有抓包事实中**未记录**，因此沿用与 UC 相同的**按请求上下文归类**策略
 * （未携带提取码 → 提示输入；已携带 → 判为提取码错误），精确区分待后续抓包细化
 * （标 TODO(用户抓包)），**不猜测 errno 数值**。
 */
@Singleton
class BaiduParser @Inject constructor(
    private val api: BaiduApi
) : NetdiskParser, ShareBrowser {

    override val type: NetdiskType = NetdiskType.BAIDU

    /**
     * 百度网盘分享链接判定：交由统一的域名正则。
     *
     * @param url 分享链接。
     * @return 是否属于百度网盘。
     */
    override fun match(url: String): Boolean =
        LinkExtractor.detectType(url) == NetdiskType.BAIDU

    /**
     * 解析百度网盘分享链接。
     *
     * 流程：短码归一化 →（有提取码则校验换 `randsk`）→ 取顶层文件列表 → 组装结果。
     *
     * @param url 分享链接。
     * @param pwd 提取码，可为 null。
     * @return 解析结果（成功 / 需要提取码 / 失败）。
     */
    override suspend fun parse(url: String, pwd: String?): ParseResult {
        val surl = LinkExtractor.extractShareId(url)
        if (surl.isBlank()) {
            return ParseResult.Error(type, CODE_INVALID_LINK, CODE_INVALID_LINK)
        }
        return withContext(Dispatchers.IO) {
            try {
                // 第 1 步：有提取码则校验并换取 sekey（randsk 已是 URL 编码形态）。
                val sekey = if (!pwd.isNullOrBlank()) {
                    val verify = api.verifyShare(
                        surl = surl,
                        pwd = pwd,
                        referer = buildShareReferer(surl)
                    )
                    if (verify.errno != BaiduApi.SUCCESS_ERRNO || verify.randsk.isNullOrBlank()) {
                        Timber.w("BaiduParser verify failed, errno=%d", verify.errno)
                        return@withContext ParseResult.Error(
                            type,
                            CODE_WRONG_PASSWORD,
                            CODE_WRONG_PASSWORD
                        )
                    }
                    verify.randsk
                } else {
                    null
                }

                // 第 2 步：取顶层文件列表（root=1；子目录才用 root=0，别写反）。
                val listed = api.listShareFiles(
                    shorturl = surl,
                    root = BaiduApi.ROOT_TOP_LEVEL,
                    sekey = sekey
                )
                if (listed.errno != BaiduApi.SUCCESS_ERRNO) {
                    // TODO(用户抓包): 补「需要提取码 / 分享已失效」的精确 errno 后改为按码判定。
                    Timber.w("BaiduParser list failed, errno=%d surl=%s", listed.errno, surl)
                    return@withContext if (pwd.isNullOrBlank()) {
                        ParseResult.NeedPassword(type)
                    } else {
                        ParseResult.Error(type, CODE_DETAIL_FAILED, CODE_DETAIL_FAILED)
                    }
                }

                ParseResult.Success(
                    netdiskType = type,
                    shareTitle = listed.title.orEmpty(),
                    files = listed.list.map { entry -> entry.toFileInfo() },
                    pwdId = surl,
                    // 百度没有独立 stoken：提取码换来的 randsk（即 sekey）承担该角色。
                    stoken = sekey.orEmpty()
                )
            } catch (cancellation: CancellationException) {
                // C3：协程取消必须原样抛出，不得吞掉
                throw cancellation
            } catch (io: IOException) {
                Timber.e(io, "BaiduParser parse failed: network error")
                ParseResult.Error(type, CODE_NETWORK, CODE_NETWORK)
            } catch (serialization: SerializationException) {
                Timber.e(serialization, "BaiduParser parse failed: unexpected response body")
                ParseResult.Error(type, CODE_PROTOCOL, CODE_PROTOCOL)
            }
        }
    }

    /**
     * 展开分享内的某个目录（子目录用 `root=0` + `dir=<完整路径>`）。
     *
     * 说明：百度以**路径**作为目录 id，因此 `pdirFid` 传入的是目录完整路径
     * （如 `/我的文件夹/子目录`）；若传入空串或 `/`，按顶层处理（`root=1`、不传 `dir`）。
     *
     * @param pwdId 分享短码。
     * @param stoken 提取码换取的 `randsk`（无提取码时为空串）。
     * @param pdirFid 目录完整路径。
     * @return 该目录下的条目列表；失败返回空列表。
     */
    override suspend fun listChildren(
        pwdId: String,
        stoken: String,
        pdirFid: String
    ): List<FileInfo> = withContext(Dispatchers.IO) {
        val isRoot = pdirFid.isBlank() || pdirFid == ROOT_DIR_PATH
        val listed = api.listShareFiles(
            shorturl = pwdId,
            root = if (isRoot) BaiduApi.ROOT_TOP_LEVEL else BaiduApi.ROOT_SUB_DIRECTORY,
            dir = if (isRoot) null else pdirFid,
            sekey = stoken.takeIf { value -> value.isNotBlank() }
        )
        if (listed.errno != BaiduApi.SUCCESS_ERRNO) {
            Timber.w(
                "BaiduParser listChildren failed, errno=%d dir=%s",
                listed.errno,
                pdirFid
            )
            return@withContext emptyList()
        }
        listed.list.map { entry -> entry.toFileInfo() }
    }

    /**
     * 构造分享页 Referer（提取码校验接口要求带分享短码）。
     *
     * @param surl 分享短码。
     * @return 形如 `https://pan.baidu.com/s/<短码>`。
     */
    private fun buildShareReferer(surl: String): String = "$SHARE_PAGE_PREFIX$surl"

    /**
     * 把百度列表条目映射为统一模型。
     *
     * 说明（《抓包事实.md》§11.3 #3）：**目录用 `path` 当 id、文件用 `fs_id`**；
     * 由于本批尚未接转存取链，`downloadUrl` 一律为 null（B3-2 补齐）。
     *
     * @return 统一文件条目。
     */
    private fun BaiduShareFile.toFileInfo(): FileInfo {
        val directory = isDirectory
        return FileInfo(
            fid = if (directory) path else fs_id?.toString().orEmpty(),
            fileName = server_filename,
            fileSize = if (directory) 0L else size,
            isDirectory = directory,
            downloadUrl = null
        )
    }

    private companion object {
        /** 分享页 URL 前缀（构造 Referer 用）。 */
        const val SHARE_PAGE_PREFIX = "https://pan.baidu.com/s/"

        /** 根目录路径（百度以 `/` 表示根）。 */
        const val ROOT_DIR_PATH = "/"

        /** 分享短码无法识别。 */
        const val CODE_INVALID_LINK = "BAIDU_INVALID_LINK"

        /** 提取码错误。 */
        const val CODE_WRONG_PASSWORD = "BAIDU_WRONG_PASSWORD"

        /** 分享列表获取失败。 */
        const val CODE_DETAIL_FAILED = "BAIDU_DETAIL_FAILED"

        /** 网络异常。 */
        const val CODE_NETWORK = "BAIDU_NETWORK_ERROR"

        /** 响应结构异常。 */
        const val CODE_PROTOCOL = "BAIDU_PROTOCOL_ERROR"
    }
}
