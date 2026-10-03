// 文件：QuarkParser.kt
// 职责：夸克网盘分享链接解析器（token → detail → save → poll → download，参数已实测校准）
// 依赖：QuarkApi、ShareTransfer、NetdiskParser、LinkExtractor、CookieStore、OkHttpClient、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.quark

import com.jieyundu.app.data.remote.CookieStore
import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.ParseResult
import com.jieyundu.app.domain.parser.NetdiskParser
import com.jieyundu.app.domain.transfer.ShareTransfer
import com.jieyundu.app.domain.util.LinkExtractor
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * 夸克网盘解析器。
 *
 * 流程（《要求.md》8.2 +《解析Bug分析.md》最小修复路径，见【修订 JYD-PARSE-2026-10-03】）：
 * 1. best-effort 请求 `https://pan.quark.cn` 首页取 `__pus` / `__puus`（合并登记）；
 * 2. 从分享链接提取 pwd_id（[LinkExtractor.extractShareId]）；
 * 3. 调 token 接口换 stoken（带 `support_visit_limit_private_share`，同时取回分享标题）；
 * 4. 调 detail 接口取文件列表（`data.detail_info.list`，兼容 `data.list`）；
 * 5. **转存**：把真实文件转存到本账号（[ShareTransfer]），拿 `task_id`；
 * 6. **轮询**：`finished_at>0`/`status==2` 后取本账号**新 fid**；
 * 7. 用**新 fid** 调 download 接口取直链；该响应还会下发 CDN 必需的 `__pugs` Cookie。
 *
 * 核心认知：`file/download` 只认自己网盘里的文件，直接传分享 fid 拿不到直链
 * —— 这就是「转存」步骤存在的唯一理由（原文第 35–36 行）。
 *
 * 仍未闭合、需人工抓包的项（铁律 R3）：提取码「需要 / 错误」的真实业务码
 * （[NEED_PASSWORD_CODE] / [WRONG_PASSWORD_CODE] 暂为占位值）。
 *
 * 线程约束：网络与 IO 全部运行在 [Dispatchers.IO]（编码风格 C9）。
 *
 * @param api 夸克接口（由 Hilt 提供，BaseUrl 与固定 Header 见 di/NetworkModule）。
 * @param shareTransfer 转存器（save + poll）。
 * @param okHttpClient 复用的 OkHttp 客户端，用于第 1 步 best-effort 取 Cookie（超时见 C4）。
 * @param cookieStore 内存态 Cookie 仓库，用于登记 `__pus`/`__puus`（`__pugs` 由网络层拦截器登记）。
 */
@Singleton
class QuarkParser @Inject constructor(
    private val api: QuarkApi,
    private val shareTransfer: ShareTransfer,
    private val okHttpClient: OkHttpClient,
    private val cookieStore: CookieStore
) : NetdiskParser {

    override val type: NetdiskType = NetdiskType.QUARK

    /**
     * 夸克分享链接判定：交由统一的域名正则，避免各处重复维护域名列表。
     *
     * @param url 分享链接。
     * @return 是否属于夸克网盘。
     */
    override fun match(url: String): Boolean =
        LinkExtractor.detectType(url) == NetdiskType.QUARK

    /**
     * 解析夸克分享链接。
     *
     * @param url 分享链接。
     * @param pwd 提取码；为 null / 空白时按「无提取码」直接向服务器尝试，
     *   仅当服务器明确要求提取码时才返回 [ParseResult.NeedPassword]（BUGFIX JYD-BUG-03-01）。
     * @return 解析结果。
     * @throws IOException 网络不可用或请求失败（由调用方决定是否重试）。
     * @throws SerializationException 响应体结构与预期不符。
     */
    override suspend fun parse(url: String, pwd: String?): ParseResult {
        val pwdId = LinkExtractor.extractShareId(url)
        if (pwdId.isBlank()) {
            return ParseResult.Error(type, CODE_INVALID_LINK, CODE_INVALID_LINK)
        }
        return withContext(Dispatchers.IO) {
            try {
                // 第 1 步：best-effort 取 __pus / __puus。实测 token/detail 无需任何 Cookie，
                // 故此处失败仅告警、不中断流程。
                registerHandshakeCookies()

                // 第 2 步：换 stoken。
                val tokenResponse = api.getShareToken(buildTokenBody(pwdId, pwd))
                when (tokenResponse.code) {
                    NEED_PASSWORD_CODE ->
                        return@withContext ParseResult.NeedPassword(type)

                    WRONG_PASSWORD_CODE ->
                        // 提取码错误：交由 UI 保留弹窗并提示重试（阶段 8 整改二）
                        return@withContext ParseResult.Error(
                            type,
                            CODE_WRONG_PASSWORD,
                            CODE_WRONG_PASSWORD
                        )

                    SUCCESS_CODE -> Unit

                    else -> return@withContext ParseResult.Error(
                        type,
                        tokenResponse.code.toString(),
                        CODE_TOKEN_FAILED
                    )
                }
                val stoken = tokenResponse.data.stoken

                // 第 3 步：取文件列表（真实结构 data.detail_info.list，兼容 data.list）。
                val detailResponse = api.getShareDetail(buildDetailParams(pwdId, stoken))
                if (detailResponse.code != SUCCESS_CODE) {
                    return@withContext ParseResult.Error(
                        type,
                        detailResponse.code.toString(),
                        CODE_DETAIL_FAILED
                    )
                }
                val entries = detailResponse.data.entries
                val shareFiles = entries.filterNot { entry -> entry.dir }

                // 无真实文件（仅文件夹或空分享）：直接返回，无需转存/取链。
                if (shareFiles.isEmpty()) {
                    return@withContext ParseResult.Success(
                        netdiskType = type,
                        shareTitle = tokenResponse.data.title,
                        files = entries.map { entry -> entry.toFileInfo(null) }
                    )
                }

                // 第 4/5 步：转存到本账号并轮询取回新 fid（不可省）。
                val newFids = shareTransfer.saveAndCollectFids(pwdId, stoken, shareFiles)
                if (newFids.isEmpty()) {
                    return@withContext ParseResult.Error(
                        type,
                        CODE_TRANSFER_FAILED,
                        CODE_TRANSFER_FAILED
                    )
                }

                // 第 6 步：用**本账号新 fid** 换取直链。
                val downloadResponse = api.getDownloadUrl(buildDownloadBody(newFids))
                if (downloadResponse.code != SUCCESS_CODE) {
                    return@withContext ParseResult.Error(
                        type,
                        downloadResponse.code.toString(),
                        CODE_DOWNLOAD_FAILED
                    )
                }
                val urlByNewFid = downloadResponse.data
                    .filter { item -> item.fid.isNotBlank() && item.download_url.isNotBlank() }
                    .associate { item -> item.fid to item.download_url }

                // 把「新 fid → 直链」按转存顺序回填到「分享文件」，供 UI 展示与下载。
                val urlByShareFid = HashMap<String, String>()
                shareFiles.forEachIndexed { index, file ->
                    val newFid = newFids.getOrNull(index) ?: return@forEachIndexed
                    urlByNewFid[newFid]?.let { url -> urlByShareFid[file.fid] = url }
                }

                ParseResult.Success(
                    netdiskType = type,
                    shareTitle = tokenResponse.data.title,
                    files = entries.map { entry -> entry.toFileInfo(urlByShareFid[entry.fid]) }
                )
            } catch (cancellation: CancellationException) {
                // C3：协程取消必须原样抛出，不得吞掉
                throw cancellation
            } catch (io: IOException) {
                Timber.e(io, "QuarkParser parse failed: network error")
                ParseResult.Error(type, CODE_NETWORK, CODE_NETWORK)
            } catch (serialization: SerializationException) {
                Timber.e(serialization, "QuarkParser parse failed: unexpected response body")
                ParseResult.Error(type, CODE_PROTOCOL, CODE_PROTOCOL)
            }
        }
    }

    /**
     * 第 1 步（best-effort）：访问夸克首页并合并取出 `__pus` 与 `__puus`。
     *
     * 实测：token 与 detail 接口**无需任何 Cookie**，故本步骤任何失败都不影响解析，
     * 仅在取到时登记以提升后续（download / CDN）链路的成功率。
     *
     * @see CookieStore.save 同域合并回写，不会互相覆盖。
     */
    private fun registerHandshakeCookies() {
        val request = Request.Builder()
            .url(QUARK_HOME_URL)
            .get()
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w(
                        "QuarkParser home request failed with code %d (cookies optional)",
                        response.code
                    )
                    return
                }
                val cookie = response.headers(HEADER_SET_COOKIE)
                    .mapNotNull { raw -> raw.substringBefore(';').trim() }
                    .filter { pair ->
                        pair.startsWith("$PUS_COOKIE_NAME=") ||
                            pair.startsWith("$PUUS_COOKIE_NAME=")
                    }
                    .joinToString(COOKIE_SEPARATOR)
                if (cookie.isNotBlank()) {
                    cookieStore.save(QUARK_COOKIE_HOST_SUFFIX, cookie)
                }
            }
        } catch (io: IOException) {
            Timber.w(io, "QuarkParser home request failed (cookies optional)")
        }
    }

    /**
     * 构造 token 接口请求体。
     *
     * 说明：
     * - 无提取码时**不带** `passcode`，交由服务器判定（BUGFIX JYD-BUG-03-01）；
     * - 恒定携带 `support_visit_limit_private_share`，否则私密分享可能取不到 stoken
     *   （《解析Bug分析.md》P1-2）。
     *
     * @param pwdId 分享 ID。
     * @param passcode 提取码；为 null / 空白表示链接未携带提取码。
     * @return 请求体键值对。
     */
    private fun buildTokenBody(pwdId: String, passcode: String?): Map<String, String> {
        val body = linkedMapOf(
            KEY_PWD_ID to pwdId,
            KEY_SUPPORT_VISIT_LIMIT_PRIVATE_SHARE to TRUE_VALUE
        )
        if (!passcode.isNullOrBlank()) {
            body[KEY_PASSCODE] = passcode
        }
        return body
    }

    /**
     * 构造 detail 接口查询参数（固定参数与排序已按实测校准）。
     *
     * @param pwdId 分享 ID。
     * @param stoken 临时令牌。
     * @return 查询参数键值对。
     */
    private fun buildDetailParams(pwdId: String, stoken: String): Map<String, String> = mapOf(
        KEY_PR to QUARK_PR,
        KEY_FR to QUARK_FR,
        KEY_PWD_ID to pwdId,
        KEY_STOKEN to stoken,
        KEY_PDIR_FID to ROOT_PDIR_FID,
        KEY_FORCE to FORCE_VALUE,
        KEY_PAGE to FIRST_PAGE,
        KEY_SIZE to PAGE_SIZE,
        KEY_SORT to DETAIL_SORT
    )

    /**
     * 构造 download 接口请求体。
     *
     * 实测：`fids` 为数组字段，传**本账号新 fid**；固定 query（pr/fr/sys/ve）已写在
     * [QuarkApi.getDownloadUrl] 的路径中。
     *
     * @param fids 转存后的本账号文件 ID 列表。
     * @return 请求体（`fids` 为数组字段）。
     */
    private fun buildDownloadBody(fids: List<String>): QuarkDownloadRequest =
        QuarkDownloadRequest(fids = fids)

    /**
     * 把夸克的文件条目映射为领域模型。
     *
     * @param downloadUrl 该文件的直链；文件夹或无直链时为 null。
     * @return 领域层文件描述。
     */
    private fun QuarkFile.toFileInfo(downloadUrl: String?): FileInfo = FileInfo(
        fid = fid,
        fileName = file_name,
        fileSize = size,
        isDirectory = dir,
        downloadUrl = downloadUrl?.takeIf { url -> url.isNotBlank() }
    )

    private companion object {
        /** 夸克首页，用于 best-effort 获取 __pus / __puus Cookie。 */
        const val QUARK_HOME_URL = "https://pan.quark.cn"

        /** Cookie 名。 */
        const val PUS_COOKIE_NAME = "__pus"
        const val PUUS_COOKIE_NAME = "__puus"

        /** 响应 Set-Cookie 头名与拼接分隔符。 */
        const val HEADER_SET_COOKIE = "Set-Cookie"
        const val COOKIE_SEPARATOR = "; "

        /** Cookie 域名后缀：同时覆盖 pan.quark.cn（握手）与 drive-pc.quark.cn（接口）。 */
        const val QUARK_COOKIE_HOST_SUFFIX = "quark.cn"

        /** 根目录的 pdir_fid 取值（实测根目录为 "0"）。 */
        const val ROOT_PDIR_FID = "0"

        /** 夸克 PC 平台固定查询参数。 */
        const val QUARK_PR = "ucpro"
        const val QUARK_FR = "pc"

        /** detail 接口固定查询参数。 */
        const val FORCE_VALUE = "0"
        const val FIRST_PAGE = "1"
        const val PAGE_SIZE = "50"
        const val DETAIL_SORT = "file_type:asc,updated_at:desc"

        /** 布尔字面量与固定取值。 */
        const val TRUE_VALUE = "true"

        /** 请求参数名。 */
        const val KEY_PWD_ID = "pwd_id"
        const val KEY_PASSCODE = "passcode"
        const val KEY_SUPPORT_VISIT_LIMIT_PRIVATE_SHARE = "support_visit_limit_private_share"
        const val KEY_STOKEN = "stoken"
        const val KEY_PDIR_FID = "pdir_fid"
        const val KEY_PR = "pr"
        const val KEY_FR = "fr"
        const val KEY_FORCE = "force"
        const val KEY_PAGE = "_page"
        const val KEY_SIZE = "_size"
        const val KEY_SORT = "_sort"

        /** 成功状态码（实测为 0）。 */
        const val SUCCESS_CODE = 0

        /**
         * 「需要提取码」状态码。
         *
         * TODO(用户抓包): 核对夸克在分享需要提取码时 token 接口返回的业务码（当前为占位值）。
         */
        const val NEED_PASSWORD_CODE = 41011

        /**
         * 「提取码错误」状态码。
         *
         * TODO(用户抓包): 核对夸克在提取码错误时 token 接口返回的业务码（当前为占位值）。
         */
        const val WRONG_PASSWORD_CODE = 41012

        /**
         * 错误码。为保持 domain 层不依赖 Android 资源系统，
         * code 与 message 均使用机器可读标识，由 UI 层映射为 strings.xml 文案。
         */
        const val CODE_INVALID_LINK = "QUARK_INVALID_LINK"
        const val CODE_TOKEN_FAILED = "QUARK_TOKEN_FAILED"
        const val CODE_DETAIL_FAILED = "QUARK_DETAIL_FAILED"
        const val CODE_TRANSFER_FAILED = "QUARK_TRANSFER_FAILED"
        const val CODE_DOWNLOAD_FAILED = "QUARK_DOWNLOAD_FAILED"
        const val CODE_WRONG_PASSWORD = "QUARK_WRONG_PASSWORD"
        const val CODE_NETWORK = "QUARK_NETWORK_ERROR"
        const val CODE_PROTOCOL = "QUARK_PROTOCOL_ERROR"
    }
}