// 文件：XunleiAuthInterceptor.kt
// 职责：迅雷请求统一注入设备头 / 验证码头 / Bearer 令牌（业务主机才带 Authorization）
// 依赖：OkHttp、XunleiConfig、XunleiFingerprint、XunleiTokenStore、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.data.remote

import com.jieyundu.app.domain.parser.xunlei.XunleiConfig
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

/**
 * 迅雷鉴权 / 设备头拦截器（【JYD-XUNLEI-P1A-2026-10-08】；Owner 硬性约束③）。
 *
 * 依据《抓包事实.md》§6.3 / §11.4：
 * - 认证与业务请求统一带 `X-Client-Id` / `X-Device-Id` / `X-Client-Version`；
 * - `X-Captcha-Token` **有才带**（验证码盾下发后才存在）；
 * - `Authorization: Bearer <access_token>` **只加在业务主机**（`api-pan`）上：
 *   认证主机（`xluser-ssl`）用 client_id/secret 换 token，按 §11.4 的「游客不写 Authorization」
 *   精神，未登录（无令牌）时**一个头也不加**，从而支持分享解析的游客模式；
 * - `Origin` / `Referer` 只加在业务主机（§11.4 统一要求）。
 *
 * 说明：设备指纹来自 [XunleiFingerprint]（一机一指纹，**不共用官方指纹**）。
 *
 * @param fingerprint 本机设备指纹。
 * @param tokenStore 登录态（access_token / captcha_token）。
 */
internal class XunleiAuthInterceptor(
    private val fingerprint: XunleiFingerprint,
    private val tokenStore: XunleiTokenStore
) : Interceptor {

    /**
     * 注入迅雷专用请求头。
     *
     * @param chain 拦截器链。
     * @return 下游响应。
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = original.url.host
        if (host != HOST_PAN && host != HOST_AUTH) {
            return chain.proceed(original)
        }

        val builder = original.newBuilder()
            .header(HEADER_CLIENT_ID, XunleiConfig.APP_CLIENT_ID)
            .header(HEADER_DEVICE_ID, fingerprint.deviceId)
            .header(HEADER_CLIENT_VERSION, XunleiConfig.APP_VERSION)
        tokenStore.captchaToken?.let { token -> builder.header(HEADER_CAPTCHA_TOKEN, token) }
        if (host == HOST_PAN) {
            builder.header(HEADER_ORIGIN, XunleiConfig.ORIGIN)
            builder.header(HEADER_REFERER, XunleiConfig.REFERER)
            // 游客匿名：无令牌时不带 Authorization（§11.4 #10）。
            tokenStore.accessToken?.let { token ->
                builder.header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$token")
            }
        }
        if (original.method != METHOD_GET) {
            // 诊断：写类调用记录是否带 Bearer / 验证码头（只记有无，不打印令牌）。
            Timber.d(
                "XunleiAuthInterceptor %s %s bearer=%s captcha=%s",
                original.method,
                original.url.encodedPath,
                tokenStore.accessToken != null,
                tokenStore.captchaToken != null
            )
        }
        return chain.proceed(builder.build())
    }

    private companion object {
        /** 业务主机（Bearer 认证）。 */
        const val HOST_PAN = "api-pan.xunlei.com"

        /** 认证主机（换 token / 刷新）。 */
        const val HOST_AUTH = "xluser-ssl.xunlei.com"

        const val HEADER_CLIENT_ID = "X-Client-Id"
        const val HEADER_DEVICE_ID = "X-Device-Id"
        const val HEADER_CLIENT_VERSION = "X-Client-Version"
        const val HEADER_CAPTCHA_TOKEN = "X-Captcha-Token"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_ORIGIN = "Origin"
        const val HEADER_REFERER = "Referer"
        const val BEARER_PREFIX = "Bearer "
        const val METHOD_GET = "GET"
    }
}
