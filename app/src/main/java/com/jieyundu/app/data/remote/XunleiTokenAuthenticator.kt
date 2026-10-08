// 文件：XunleiTokenAuthenticator.kt
// 职责：业务接口 401 时用 refresh_token 换新令牌并重试一次（§11.4 要点②）
// 依赖：OkHttp Authenticator、XunleiTokenStore、XunleiLoginManager（懒解析）
// 协议：AGPL-3.0

package com.jieyundu.app.data.remote

import com.jieyundu.app.domain.login.XunleiLoginManager
import javax.inject.Provider
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber

/**
 * 迅雷 401 自动续期（【JYD-XUNLEI-P1B-2026-10-08】；Owner 约束⑤ / §11.4 要点②）。
 *
 * 行为：
 * - **只处理业务主机**（`api-pan.xunlei.com`）的 **401**；认证主机与游客匿名请求一律不处理；
 * - 已有令牌才尝试续期（未登录 = 游客，不刷新、不带 Bearer）；
 * - 续期成功后用新令牌**重试一次**（`priorResponse != null` 即不再重试，避免死循环）；
 * - 续期失败返回 null：请求以 401 结束，由上层提示「登录已过期，请重新登录」。
 *
 * @param tokenStore 登录态存储。
 * @param loginManagerProvider 登录管理器（懒解析，打破 OkHttpClient ↔ Retrofit 循环）。
 */
internal class XunleiTokenAuthenticator(
    private val tokenStore: XunleiTokenStore,
    private val loginManagerProvider: Provider<XunleiLoginManager>
) : Authenticator {

    /**
     * 处理 401：续期并重试一次。
     *
     * @param route 路由（未使用）。
     * @param response 收到 401 的响应。
     * @return 带新令牌的请求；不处理或续期失败时返回 null。
     */
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.host != HOST_PAN) {
            return null
        }
        if (response.code != HTTP_UNAUTHORIZED) {
            return null
        }
        if (response.priorResponse != null) {
            // 已经重试过一次仍 401：不再尝试，避免无限循环。
            return null
        }
        if (!tokenStore.isLoggedIn) {
            // 游客匿名请求（§11.4 #10）：不带 Bearer，也不刷新。
            return null
        }
        val refreshed = loginManagerProvider.get().refreshSessionBlocking()
        if (!refreshed) {
            Timber.w("XunleiTokenAuthenticator refresh failed, request stays unauthorized")
            return null
        }
        val token = tokenStore.accessToken ?: return null
        Timber.i("XunleiTokenAuthenticator retrying request with refreshed token")
        return response.request.newBuilder()
            .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$token")
            .build()
    }

    private companion object {
        /** 业务主机（Bearer 认证）。 */
        const val HOST_PAN = "api-pan.xunlei.com"

        /** 未授权状态码。 */
        const val HTTP_UNAUTHORIZED = 401

        const val HEADER_AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
