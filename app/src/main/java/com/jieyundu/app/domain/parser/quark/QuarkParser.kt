// 文件：QuarkParser.kt
// 职责：夸克网盘分享链接解析器（优先实现网盘；网络参数待用户抓包后补全）
// 依赖：QuarkApi、NetdiskParser、LinkExtractor、OkHttpClient、Timber
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
 * 流程（见《要求.md》8.2）：
 * 1. 请求 `https://pan.quark.cn` 首页，取 `__puus` Cookie；
 * 2. 从分享链接提取 pwd_id（本项目的 [LinkExtractor.extractShareId]）；
 * 3. 调 token 接口换 stoken；
 * 4. 调 detail 接口取文件列表；
 * 5. 调 download 接口取直链；
 * 6. 交由下载引擎分片下载。
 *
 * ⚠️ 当前为**骨架实现**：凡涉及真实参数、风控规则、字段名的位置一律以
 * `TODO(用户抓包):` 标注，等待抓包数据填入（铁律 R3）。
 *
 * 线程约束：网络与 IO 全部运行在 [Dispatchers.IO]（编码风格 C9）。
 *
 * Cookie 传递（依据整改指令硬伤 1 · 方案 B）：第 1 步拿到的 `__puus` 写入
 * [CookieStore]，由 di/NetworkModule 中的 CookieInterceptor 按域名注入，
 * 从而在不改动 QuarkApi 签名（《要求.md》7.6）的前提下把 Cookie 送到后续请求。
 *
 * @param api 夸克接口（由 Hilt 提供，BaseUrl 与固定 Header 见 di/NetworkModule）。
 * @param okHttpClient 复用的 OkHttp 客户端，用于第 1 步取 Cookie（超时见 C4）。
 * @param cookieStore 内存态 Cookie 仓库，用于登记 `__puus`。
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
     * @param pwd 提取码；为 null 时直接返回 [ParseResult.NeedPassword]，不发请求。
     * @return 解析结果。
     * @throws IOException 网络不可用或请求失败（由调用方决定是否重试）。
     * @throws SerializationException 响应体结构与预期不符。
     */
    override suspend fun parse(url: String, pwd: String?): ParseResult {
        val pwdId = LinkExtractor.extractShareId(url)
        if (pwdId.isBlank()) {
            return ParseResult.Error(type, CODE_INVALID_LINK, CODE_INVALID_LINK)
        }
        if (pwd.isNullOrBlank()) {
            return ParseResult.NeedPassword(type)
        }

        return withContext(Dispatchers.IO) {
            try {
                val puusCookie = requestPuusCookie()
                if (puusCookie == null) {
                    // TODO(用户抓包): 确认缺少 __puus 时夸克是否必然拒绝；若可缺省则放宽为告警
                    return@withContext ParseResult.Error(type, CODE_NEED_COOKIE, CODE_NEED_COOKIE)
                }
                // 硬伤 1 修复（方案 B）：把 __puus 登记到仓库，
                // 由 di/NetworkModule 的 CookieInterceptor 注入后续接口请求。
                cookieStore.save(QUARK_COOKIE_HOST_SUFFIX, puusCookie)

                val tokenResponse = api.getShareToken(buildTokenBody(pwdId, pwd))
                if (tokenResponse.code != SUCCESS_CODE) {
                    return@withContext ParseResult.Error(
                        type,
                        tokenResponse.code.toString(),
                        CODE_TOKEN_FAILED
                    )
                }
                val stoken = tokenResponse.data.stoken

                val detailResponse = api.getShareDetail(buildDetailParams(pwdId, stoken))
                if (detailResponse.code != SUCCESS_CODE) {
                    return@withContext ParseResult.Error(
                        type,
                        detailResponse.code.toString(),
                        CODE_DETAIL_FAILED
                    )
                }

                ParseResult.Success(
                    netdiskType = type,
                    // TODO(用户抓包): 分享标题字段名（当前占位空串，UI 侧以文件名兜底）
                    shareTitle = SHARE_TITLE_PLACEHOLDER,
                    files = detailResponse.data.list.map { quarkFile -> quarkFile.toFileInfo() }
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
     * 第 1 步：访问夸克首页并取出 `__puus` Cookie。
     *
     * // TODO(用户抓包): 核对是否需要改为先访问分享页 /s/{pwdId} 才能拿到可用 __puus
     * // TODO(用户抓包): 核对是否需要额外 Header（如 Referer、Accept-Language）
     *
     * @return `__puus` 的键值对（形如 `__puus=xxx`）；失败时返回 null。
     */
    private fun requestPuusCookie(): String? {
        val request = Request.Builder()
            .url(QUARK_HOME_URL)
            .get()
            .build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("QuarkParser home request failed with code %d", response.code)
                    return null
                }
                response.headers("Set-Cookie")
                    .firstOrNull { cookie -> cookie.startsWith(PUUS_COOKIE_NAME) }
                    ?.substringBefore(';')
            }
        } catch (io: IOException) {
            Timber.e(io, "QuarkParser home request failed")
            null
        }
    }

    /**
     * 构造 token 接口请求体。
     *
     * // TODO(用户抓包): 补齐固定参数并核对参数名大小写与类型
     *
     * @param pwdId 分享 ID。
     * @param passcode 提取码。
     * @return 请求体键值对。
     */
    private fun buildTokenBody(pwdId: String, passcode: String): Map<String, String> = mapOf(
        KEY_PWD_ID to pwdId,
        KEY_PASSCODE to passcode
    )

    /**
     * 构造 detail 接口查询参数。
     *
     * // TODO(用户抓包): 补齐固定查询参数，并确认根目录 pdir_fid 的取值（当前按 `0` 占位）
     *
     * @param pwdId 分享 ID。
     * @param stoken 临时令牌。
     * @return 查询参数键值对。
     */
    private fun buildDetailParams(pwdId: String, stoken: String): Map<String, String> = mapOf(
        KEY_PWD_ID to pwdId,
        KEY_STOKEN to stoken,
        KEY_PDIR_FID to ROOT_PDIR_FID
    )

    /**
     * 把夸克的文件条目映射为领域模型。
     *
     * // TODO(用户抓包): 补齐文件夹判定字段；文件夹不应进入下载队列
     *
     * @return 领域层文件描述。
     */
    private fun QuarkFile.toFileInfo(): FileInfo = FileInfo(
        fid = fid,
        fileName = file_name,
        fileSize = size,
        isDirectory = false,
        downloadUrl = null
    )

    private companion object {
        /** 夸克首页，用于获取 __puus Cookie。 */
        const val QUARK_HOME_URL = "https://pan.quark.cn"

        /** __puus Cookie 名前缀。 */
        const val PUUS_COOKIE_NAME = "__puus"

        /** Cookie 域名后缀：同时覆盖 pan.quark.cn（握手）与 drive-pc.quark.cn（接口）。 */
        const val QUARK_COOKIE_HOST_SUFFIX = "quark.cn"

        /** 根目录的 pdir_fid 占位值。 */
        const val ROOT_PDIR_FID = "0"

        /** 分享标题占位值。 */
        const val SHARE_TITLE_PLACEHOLDER = ""

        /** 请求参数名。 */
        const val KEY_PWD_ID = "pwd_id"
        const val KEY_PASSCODE = "passcode"
        const val KEY_STOKEN = "stoken"
        const val KEY_PDIR_FID = "pdir_fid"

        /** 成功状态码。 */
        // TODO(用户抓包): 核对夸克成功码是否为 0
        const val SUCCESS_CODE = 0

        /**
         * 错误码。为保持 domain 层不依赖 Android 资源系统，
         * code 与 message 均使用机器可读标识，由 UI 层映射为 strings.xml 文案。
         */
        const val CODE_INVALID_LINK = "QUARK_INVALID_LINK"
        const val CODE_NEED_COOKIE = "QUARK_NEED_COOKIE"
        const val CODE_TOKEN_FAILED = "QUARK_TOKEN_FAILED"
        const val CODE_DETAIL_FAILED = "QUARK_DETAIL_FAILED"
        const val CODE_NETWORK = "QUARK_NETWORK_ERROR"
        const val CODE_PROTOCOL = "QUARK_PROTOCOL_ERROR"
    }
}
