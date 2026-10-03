// 文件：QuarkParser.kt
// 职责：夸克网盘分享链接解析器（参数与字段已按真实接口实测校准）
// 依赖：QuarkApi、NetdiskParser、LinkExtractor、CookieStore、OkHttpClient、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.quark

import com.jieyundu.app.data.remote.CookieStore
import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.ParseResult
import com.jieyundu.app.domain.parser.NetdiskParser
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
 * 流程（见《要求.md》8.2，已按 2026-10-03 终端实测校准）：
 * 1. best-effort 请求 `https://pan.quark.cn` 首页取 `__puus`（**实测 token/detail 无需任何
 *    Cookie**，故拿不到时仅告警、不再中断）；
 * 2. 从分享链接提取 pwd_id（[LinkExtractor.extractShareId]）；
 * 3. 调 token 接口换 stoken（同时取回分享标题 title）；
 * 4. 调 detail 接口取文件列表（含 `dir` 文件夹标记）；
 * 5. 对真实文件（非文件夹）调 download 接口换取直链；该接口响应还会下发 CDN 直链必需的
 *    `__pugs` Cookie，由 di/NetworkModule 的响应拦截器登记到 [CookieStore]；
 * 6. 交由下载引擎分片下载。
 *
 * 仍未闭合、需人工抓包的项（铁律 R3）：提取码「需要 / 错误」的真实业务码
 * （[NEED_PASSWORD_CODE] / [WRONG_PASSWORD_CODE] 暂为占位值）。
 *
 * 线程约束：网络与 IO 全部运行在 [Dispatchers.IO]（编码风格 C9）。
 *
 * @param api 夸克接口（由 Hilt 提供，BaseUrl 与固定 Header 见 di/NetworkModule）。
 * @param okHttpClient 复用的 OkHttp 客户端，用于第 1 步 best-effort 取 Cookie（超时见 C4）。
 * @param cookieStore 内存态 Cookie 仓库，用于登记 `__puus`（`__pugs` 由网络层拦截器登记）。
 */
@Singleton
class QuarkParser @Inject constructor(
    private val api: QuarkApi,
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
                // 第 1 步：best-effort 取 __puus。实测 token/detail 无需任何 Cookie，
                // 故此处失败仅告警、不中断流程（《要求.md》8.2 TODO：若可缺省则放宽为告警）。
                registerPuusCookie()

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

                // 第 3 步：取文件列表。
                val detailResponse = api.getShareDetail(buildDetailParams(pwdId, stoken))
                if (detailResponse.code != SUCCESS_CODE) {
                    return@withContext ParseResult.Error(
                        type,
                        detailResponse.code.toString(),
                        CODE_DETAIL_FAILED
                    )
                }
                val entries = detailResponse.data.list

                // 第 4 步：仅为真实文件（非文件夹）换取直链。
                val fileFids = entries.filterNot { entry -> entry.dir }.map { entry -> entry.fid }
                val downloadUrls: Map<String, String> = if (fileFids.isEmpty()) {
                    emptyMap()
                } else {
                    val downloadResponse = api.getDownloadUrl(
                        buildDownloadBody(fileFids, pwdId, stoken)
                    )
                    if (downloadResponse.code != SUCCESS_CODE) {
                        return@withContext ParseResult.Error(
                            type,
                            downloadResponse.code.toString(),
                            CODE_DOWNLOAD_FAILED
                        )
                    }
                    downloadResponse.data
                        .filter { item -> item.fid.isNotBlank() && item.download_url.isNotBlank() }
                        .associate { item -> item.fid to item.download_url }
                }

                ParseResult.Success(
                    netdiskType = type,
                    shareTitle = tokenResponse.data.title,
                    files = entries.map { entry -> entry.toFileInfo(downloadUrls[entry.fid]) }
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
     * 第 1 步（best-effort）：访问夸克首页并取出 `__puus` Cookie。
     *
     * 实测（2026-10-03）：token 与 detail 接口**无需任何 Cookie**，故本步骤已由
     * 硬门槛放宽为「取到就登记、取不到仅告警」，任何失败都不影响解析流程。
     *
     * 说明：`__puus` 若成功取得，会写入 [CookieStore] 供后续请求按域名注入；
     * 随后的 download 响应下发的 `__pugs` 会覆盖同一域名后缀下的 Cookie 串，
     * 因 CDN 直链仅依赖 `__pugs`，该覆盖对下载链路无影响。
     */
    private fun registerPuusCookie() {
        val request = Request.Builder()
            .url(QUARK_HOME_URL)
            .get()
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w(
                        "QuarkParser home request failed with code %d (__puus optional)",
                        response.code
                    )
                    return
                }
                response.headers("Set-Cookie")
                    .firstOrNull { cookie -> cookie.startsWith(PUUS_COOKIE_NAME) }
                    ?.substringBefore(';')
                    ?.let { cookie -> cookieStore.save(QUARK_COOKIE_HOST_SUFFIX, cookie) }
            }
        } catch (io: IOException) {
            Timber.w(io, "QuarkParser home request failed (__puus optional)")
        }
    }

    /**
     * 构造 token 接口请求体。
     *
     * 说明（BUGFIX JYD-BUG-03-01）：无提取码时**不带** `passcode` 字段，
     * 交由服务器判定该分享是否需要提取码；只有服务器返回「需要提取码」时，
     * 上层才提示用户输入。
     *
     * @param pwdId 分享 ID。
     * @param passcode 提取码；为 null / 空白表示链接未携带提取码。
     * @return 请求体键值对。
     */
    private fun buildTokenBody(pwdId: String, passcode: String?): Map<String, String> =
        if (passcode.isNullOrBlank()) {
            mapOf(KEY_PWD_ID to pwdId)
        } else {
            mapOf(KEY_PWD_ID to pwdId, KEY_PASSCODE to passcode)
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
     * 实测：`fids` 为数组字段，且**必须**同时携带 `pwd_id` 与 `stoken`，
     * 否则返回 `code:31001 require login [share missing]`。
     *
     * @param fids 待换取直链的文件 ID 列表（已剔除文件夹）。
     * @param pwdId 分享 ID。
     * @param stoken 临时令牌。
     * @return 请求体键值对（值为任意类型，含数组）。
     */
    private fun buildDownloadBody(
        fids: List<String>,
        pwdId: String,
        stoken: String
    ): Map<String, Any> = mapOf(
        KEY_FIDS to fids,
        KEY_PWD_ID to pwdId,
        KEY_STOKEN to stoken
    )

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
        /** 夸克首页，用于 best-effort 获取 __puus Cookie。 */
        const val QUARK_HOME_URL = "https://pan.quark.cn"

        /** __puus Cookie 名前缀。 */
        const val PUUS_COOKIE_NAME = "__puus"

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

        /** 请求参数名。 */
        const val KEY_PWD_ID = "pwd_id"
        const val KEY_PASSCODE = "passcode"
        const val KEY_STOKEN = "stoken"
        const val KEY_PDIR_FID = "pdir_fid"
        const val KEY_PR = "pr"
        const val KEY_FR = "fr"
        const val KEY_FORCE = "force"
        const val KEY_PAGE = "_page"
        const val KEY_SIZE = "_size"
        const val KEY_SORT = "_sort"
        const val KEY_FIDS = "fids"

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
        const val CODE_DOWNLOAD_FAILED = "QUARK_DOWNLOAD_FAILED"
        const val CODE_WRONG_PASSWORD = "QUARK_WRONG_PASSWORD"
        const val CODE_NETWORK = "QUARK_NETWORK_ERROR"
        const val CODE_PROTOCOL = "QUARK_PROTOCOL_ERROR"
    }
}