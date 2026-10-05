// 文件：LoginValidator.kt
// 职责：登录态网络校验——拿候选 Cookie 调轻量只读接口，确认登录是否真正成立
// 依赖：OkHttpClient、UserAgentProvider、NetdiskType、协程、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.login

import com.jieyundu.app.data.remote.UserAgentProvider
import com.jieyundu.app.domain.model.NetdiskType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * 登录态网络校验器（依据《WebView登录与Cookie提取实践》§4.2 +《抓包事实.md》）。
 *
 * 核心认知：「Cookie 出现」≠「登录成功」。必须拿候选 Cookie 真实请求一个轻量只读
 * 接口，返回成功才判定登录成立。本类即承担该「第二级校验」。
 *
 * 校验接口（《抓包事实.md》第 1、2 节与 §11.3 #1）：
 * - 夸克：`https://pan.quark.cn/account/info`
 * - UC  ：`https://drive.uc.cn/account/info`
 * - 百度：`https://pan.baidu.com/api/gettemplatevariable`（`fields=["bdstoken"]`）——
 *   该接口是**只读探测**：已登录时返回 `result.bdstoken` / `result.username`，
 *   未登录则不带该字段。此前百度没有校验端点，[validate] 因 `url == null` 恒返回 false，
 *   导致**百度 Cookie 永远存不进**（【JYD-BAIDU-COOKIE-2026-10-05】修复）。
 *
 * 说明：请求显式携带候选 Cookie（不依赖 CookieStore，避免把未校验的凭证提前落库）；
 * OkHttpClient 复用全局单例（其 CookieInterceptor 检测到已有 Cookie 头时不会覆盖）。
 *
 * @param okHttpClient 全局 HTTP 客户端。
 * @param userAgentProvider UA 常量提供者。
 */
@Singleton
class LoginValidator @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val userAgentProvider: UserAgentProvider
) {

    /**
     * 校验候选 Cookie 是否代表某网盘登录成立。
     *
     * @param type 网盘类型。
     * @param cookie 候选 Cookie 串。
     * @return true 表示网络校验通过（登录成立）。
     */
    suspend fun validate(type: NetdiskType, cookie: String): Boolean =
        withContext(Dispatchers.IO) {
            val url = validationUrlOf(type)
            if (url == null || cookie.isBlank()) {
                Timber.w("LoginValidator no endpoint/cookie for %s", type)
                return@withContext false
            }
            val request = Request.Builder()
                .url(url)
                .header(HEADER_COOKIE, cookie)
                .apply {
                    // UA 按网盘取值：百度用网页 UA（§11.3 #1）；
                    // 夸克 / UC **保持既有取值不变**（两家登录链路已验收，本批不动）。
                    val agent = userAgentOf(type)
                    if (agent.isNotBlank()) {
                        header(HEADER_USER_AGENT, agent)
                    }
                }
                .get()
                .build()
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val passed = response.isSuccessful && body.contains(markerOf(type))
                    Timber.d(
                        "LoginValidator %s http=%d passed=%b",
                        type,
                        response.code,
                        passed
                    )
                    passed
                }
            } catch (cancellation: CancellationException) {
                // 协程取消语义必须原样抛出（《实践》§5）。
                throw cancellation
            } catch (error: Exception) {
                // 其余异常按「本轮校验失败」处理，不崩溃、继续轮询。
                Timber.e(error, "LoginValidator request failed for %s", type)
                false
            }
        }

    /**
     * 各网盘登录校验接口 URL。
     *
     * @param type 网盘类型。
     * @return 校验接口 URL；未支持的平台返回 null。
     */
    internal fun validationUrlOf(type: NetdiskType): String? = when (type) {
        NetdiskType.QUARK -> "https://pan.quark.cn/account/info"
        NetdiskType.UC -> "https://drive.uc.cn/account/info"
        // 百度：gettemplatevariable 是只读探测接口（§11.3 #1）；
        // fields 的方括号与引号按 URL 规范百分号编码。
        NetdiskType.BAIDU -> "https://pan.baidu.com/api/gettemplatevariable" +
            "?clienttype=0&app_id=250528&web=1&fields=%5B%22bdstoken%22%5D"
        // 迅雷登录态是 access_token（JWT），非 Cookie，本校验不适用。
        NetdiskType.XUNLEI -> null
    }

    /**
     * 登录成功的响应判据（各网盘字段名不同）。
     *
     * @param type 网盘类型。
     * @return 成功响应中必现的字段字面量。
     */
    internal fun markerOf(type: NetdiskType): String = when (type) {
        // 百度：已登录才返回 result.bdstoken / result.username。
        NetdiskType.BAIDU -> MARKER_BAIDU_BDSTOKEN
        else -> MARKER_LOGGED_IN_DATA
    }

    /**
     * 校验请求使用的 User-Agent。
     *
     * 说明：百度用**网页 UA**（《抓包事实.md》§11.3 #1）；夸克 / UC 沿用既有的
     * `quarkUserAgent`——两家登录链路已验收，本批**刻意不改**其取值以避免回归
     * （UC 使用夸克 UA 属历史遗留不一致，列为后续单独清理项）。
     *
     * @param type 网盘类型。
     * @return UA 字符串；空串表示不设置该头（交由网络层按 host 选择）。
     */
    internal fun userAgentOf(type: NetdiskType): String = when (type) {
        NetdiskType.BAIDU -> userAgentProvider.webUserAgentOf(type)
        else -> userAgentProvider.quarkUserAgent
    }

    private companion object {
        /** Cookie 请求头名。 */
        const val HEADER_COOKIE = "Cookie"

        /** User-Agent 请求头名。 */
        const val HEADER_USER_AGENT = "User-Agent"

        /**
         * 登录成功响应的标志字段。
         *
         * 说明：`account/info` 成功时返回含用户信息的 `data` 字段；未登录时不携带。
         * 该判据为启发式，待 Owner 实测后按真实响应体校准。
         */
        const val MARKER_LOGGED_IN_DATA = "\"data\""

        /**
         * 百度登录成功的标志字段。
         *
         * 依据：《抓包事实.md》§11.3 #1——`gettemplatevariable` 返回
         * `result.bdstoken` / `result.username`，未登录不带。
         */
        const val MARKER_BAIDU_BDSTOKEN = "\"bdstoken\""
    }
}