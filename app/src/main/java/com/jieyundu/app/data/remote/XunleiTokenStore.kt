// 文件：XunleiTokenStore.kt
// 职责：迅雷 access_token / refresh_token / captcha_token 的加密持久化与过期判定（JWT exp）
// 依赖：XunleiConfig、JwtExpiry、EncryptedSharedPreferences、Hilt、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jieyundu.app.domain.parser.xunlei.XunleiConfig
import com.jieyundu.app.domain.util.JwtExpiry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * 迅雷登录态存储（【JYD-XUNLEI-P1A-2026-10-08】；Owner 硬性约束②）。
 *
 * 与夸克 / UC / 百度的 **Cookie 通道不同**：迅雷业务接口用 `Authorization: Bearer <access_token>`
 * （《抓包事实.md》§11.4），因此登录态是 **OAuth2 双令牌**：
 * - `access_token`：JWT，带 `exp`——本类用 [JwtExpiry] 判断是否过期；
 * - `refresh_token`：过期时用它换新（端点 `POST /v1/auth/token`，form 表单，§11.4 #6）；
 * - `captcha_token`：验证码盾下发（`X-Captcha-Token` 头「有才带」，§6.3）。
 *
 * 安全：三个值一律走 [EncryptedSharedPreferences]（AES-256，encrypted at rest）；
 * 存储不可用时降级为纯内存（登录态本次进程有效，不落盘、不崩溃）。
 *
 * 线程安全：`SharedPreferences` 本身线程安全；内存缓存用 `@Volatile`。
 *
 * @param prefs 加密偏好存储；为 null 时退化为纯内存。
 */
@Singleton
class XunleiTokenStore internal constructor(
    private val prefs: SharedPreferences?
) {

    /** 内存缓存：access_token（避免每请求都解密读盘）。 */
    @Volatile
    private var cachedAccessToken: String? = prefs?.getString(KEY_ACCESS_TOKEN, null)

    /** 内存缓存：refresh_token。 */
    @Volatile
    private var cachedRefreshToken: String? = prefs?.getString(KEY_REFRESH_TOKEN, null)

    /** 内存缓存：captcha_token。 */
    @Volatile
    private var cachedCaptchaToken: String? = prefs?.getString(KEY_CAPTCHA_TOKEN, null)

    /** 内存缓存：nickname（§4 ③：客户端本地库持久化 nickname）。 */
    @Volatile
    private var cachedNickname: String? = prefs?.getString(KEY_NICKNAME, null)

    /**
     * 应用上下文构造（生产路径）。
     *
     * @param context 应用上下文。
     */
    @Inject
    constructor(@ApplicationContext context: Context) :
        this(createEncryptedPreferences(context))

    /** 当前 access_token；未登录时为 null。 */
    val accessToken: String? get() = cachedAccessToken?.takeIf { value -> value.isNotBlank() }

    /** 当前 refresh_token；未登录时为 null。 */
    val refreshToken: String? get() = cachedRefreshToken?.takeIf { value -> value.isNotBlank() }

    /** 当前 captcha_token；未取得时为 null。 */
    val captchaToken: String? get() = cachedCaptchaToken?.takeIf { value -> value.isNotBlank() }

    /** 昵称（登录成功后记录；用于「网盘」页展示与重登提示）。 */
    val nickname: String? get() = cachedNickname?.takeIf { value -> value.isNotBlank() }

    /**
     * 用户 id：直接从 access_token 的 JWT `sub` 解出（§4 ③/§12.4 `cacheUserId`）。
     *
     * 说明：不额外落盘——`sub` 本就是令牌自带信息，另行存储反而可能不一致。
     *
     * @return 用户 id；未登录或无法解析时 null。
     */
    val userId: String? get() = accessToken?.let { token -> JwtExpiry.subject(token) }

    /** 是否已有可用的 access_token（不论是否临近过期）。 */
    val isLoggedIn: Boolean get() = accessToken != null

    /**
     * access_token 是否已过期（含 60 秒提前量）。
     *
     * @return true 表示需要刷新（或未登录 / 无法解析 exp）。
     */
    fun isAccessTokenExpired(): Boolean {
        val token = accessToken ?: return true
        return JwtExpiry.isExpired(token)
    }

    /**
     * access_token 的过期时刻（诊断 / 日志用）。
     *
     * @return epoch 毫秒；未知时 null。
     */
    fun accessTokenExpiresAtMillis(): Long? = accessToken?.let { token -> JwtExpiry.expiresAtMillis(token) }

    /**
     * 保存一对令牌（登录成功或刷新成功后调用）。
     *
     * @param accessToken 新的 access_token。
     * @param refreshToken 新的 refresh_token；服务端未下发时传 null（保留旧值）。
     */
    fun saveTokens(accessToken: String, refreshToken: String?) {
        cachedAccessToken = accessToken
        write(KEY_ACCESS_TOKEN, accessToken)
        if (!refreshToken.isNullOrBlank()) {
            cachedRefreshToken = refreshToken
            write(KEY_REFRESH_TOKEN, refreshToken)
        }
    }

    /**
     * 保存验证码盾令牌（`captcha/init` 成功后调用）。
     *
     * @param token captcha_token；null 表示清除。
     */
    fun saveCaptchaToken(token: String?) {
        cachedCaptchaToken = token
        write(KEY_CAPTCHA_TOKEN, token)
    }

    /**
     * 保存昵称（登录成功后调用）。
     *
     * @param nickname 昵称；null 表示清除。
     */
    fun saveNickname(nickname: String?) {
        cachedNickname = nickname
        write(KEY_NICKNAME, nickname)
    }

    /** 清除全部登录态（退出登录 / 刷新失败提示重登时调用）。 */
    fun clear() {
        cachedAccessToken = null
        cachedRefreshToken = null
        cachedCaptchaToken = null
        cachedNickname = null
        runCatching { prefs?.edit()?.clear()?.apply() }
            .onFailure { error -> Timber.w(error, "XunleiTokenStore clear failed") }
    }

    /**
     * 写入偏好（失败仅记日志，不影响本次进程内的内存态）。
     *
     * @param key 键。
     * @param value 值；null 表示移除。
     */
    private fun write(key: String, value: String?) {
        val editor = prefs?.edit() ?: return
        if (value == null) {
            editor.remove(key)
        } else {
            editor.putString(key, value)
        }
        runCatching { editor.apply() }
            .onFailure { error -> Timber.w(error, "XunleiTokenStore persist failed for %s", key) }
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_CAPTCHA_TOKEN = "captcha_token"
        const val KEY_NICKNAME = "nickname"

        /**
         * 创建加密偏好存储；不可用时返回 null（纯内存降级）。
         *
         * ponytail: 与 `CookieStore` / `XunleiFingerprint` 的私有实现同形（约 12 行重复）；
         * 本批铁律要求「不动夸克 / UC 代码」，故不抽取公共 helper，待条件允许再统一。
         *
         * @param context 应用上下文。
         * @return 加密偏好；失败返回 null。
         */
        fun createEncryptedPreferences(context: Context): SharedPreferences? = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                XunleiConfig.PREFS_FILE_TOKENS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (exception: Exception) {
            Timber.e(exception, "XunleiTokenStore encrypted prefs unavailable, fallback to memory only")
            null
        }
    }
}
