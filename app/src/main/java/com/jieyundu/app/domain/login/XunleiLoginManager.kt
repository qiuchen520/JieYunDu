// 文件：XunleiLoginManager.kt
// 职责：迅雷原生登录与会话管理（验证码盾 → 登录/短信 → 换 token；刷新；登出）
// 依赖：XunleiAuthApi、XunleiTokenStore、XunleiFingerprint、XunleiSigning、JwtExpiry、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.login

import com.jieyundu.app.data.remote.XunleiFingerprint
import com.jieyundu.app.data.remote.XunleiTokenStore
import com.jieyundu.app.domain.parser.xunlei.XunleiAuthApi
import com.jieyundu.app.domain.parser.xunlei.XunleiCaptchaInitRequest
import com.jieyundu.app.domain.parser.xunlei.XunleiCaptchaMeta
import com.jieyundu.app.domain.parser.xunlei.XunleiConfig
import com.jieyundu.app.domain.parser.xunlei.XunleiExchangeTokenRequest
import com.jieyundu.app.domain.parser.xunlei.XunleiLoginRequest
import com.jieyundu.app.domain.parser.xunlei.XunleiLoginResponse
import com.jieyundu.app.domain.parser.xunlei.XunleiSendSmsRequest
import com.jieyundu.app.domain.parser.xunlei.XunleiSmsLoginRequest
import com.jieyundu.app.domain.parser.xunlei.XunleiSigning
import com.jieyundu.app.domain.parser.xunlei.XunleiTokenResponse
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * 迅雷登录结果。
 *
 * 说明：UI 只需按穷尽 `when` 分支展示，不需要理解协议细节。
 */
sealed interface XunleiLoginResult {

    /**
     * 登录成功（令牌已持久化）。
     *
     * @property nickname 服务端返回的昵称。
     */
    data class Success(val nickname: String?) : XunleiLoginResult

    /**
     * 触发安全验证（风控）：需要内嵌 WebView 完成验证后再登录。
     *
     * @property reviewUrl 安全验证页地址（服务端下发 `reviewurl`）。
     */
    data class NeedsReview(val reviewUrl: String?) : XunleiLoginResult

    /**
     * 短信已发出（等待用户填验证码）。
     *
     * @property token 短信流程令牌，短信登录时原样回传。
     */
    data class SmsSent(val token: String?) : XunleiLoginResult

    /**
     * 失败。
     *
     * @property code 本模块错误码（UI 映射文案）。
     * @property message 可直接展示的详情（优先服务端原文）。
     */
    data class Failure(val code: String, val message: String) : XunleiLoginResult
}

/**
 * 迅雷原生登录 / 会话管理器（【JYD-XUNLEI-P1B-2026-10-08】）。
 *
 * 链路（《抓包事实.md》§4「登录链路」①，**无 OAuth 授权码**）：
 * ```
 * 1) POST /xluser.core.login/v3/login 或 smslogin  → sessionID + userID + nickName
 *    （风控：errorCode=1007 + reviewurl → 交 WebView 处理）
 * 2) POST /v1/shield/captcha/init                  → captcha_token
 *    （meta.user_id 必须为真实 user_id，否则拿到降级 token；故放在登录之后）
 * 3) POST /v1/auth/signin/token（带 X-Captcha-Token）→ access_token + refresh_token
 * ```
 * 顺序说明：文档 §4 ① 的 step0 把 `captcha/init` 列在最前，但同节又注明
 * 「`meta.user_id` 必须真实、为空会降级」以及「官方时序：`smslogin → captcha/init → signin/token`」；
 * 本实现取后者（先登录拿到 userID，再 init，再换 token），以**满足 user_id 约束**为准。
 *
 * 刷新（§11.4 要点②）：401 或 JWT 到期 → `POST /v1/auth/token`（refresh_token 模式）
 * → 成功后**重新 init captcha**（携带新的 user_id）→ 重试原请求一次。
 *
 * @param authApi 认证主机接口。
 * @param tokenStore 登录态存储（加密持久化）。
 * @param fingerprint 本机设备指纹（devicesign / device_id 来源）。
 */
@Singleton
class XunleiLoginManager @Inject constructor(
    private val authApi: XunleiAuthApi,
    private val tokenStore: XunleiTokenStore,
    private val fingerprint: XunleiFingerprint
) {

    /** 登录态内部状态流（先于只读流声明，避免初始化顺序问题）。 */
    private val loggedInState = MutableStateFlow(tokenStore.isLoggedIn)

    /** 会话是否可用（供 UI 在重启后回显登录态）。 */
    val isLoggedIn: StateFlow<Boolean> = loggedInState.asStateFlow()

    /**
     * 账号密码登录（完整链路，成功即已持久化令牌）。
     *
     * @param userName 账号。
     * @param password 密码。
     * @return 登录结果。
     */
    suspend fun loginWithPassword(userName: String, password: String): XunleiLoginResult =
        runLogin {
            authApi.loginWithPassword(
                XunleiLoginRequest(userName = userName, passWord = password)
            )
        }

    /**
     * 发送短信验证码（短信登录第一步）。
     *
     * @param mobile 手机号。
     * @return 短信结果（成功为 [XunleiLoginResult.SmsSent]）。
     */
    suspend fun sendSms(mobile: String): XunleiLoginResult = try {
        val response = authApi.sendSms(XunleiSendSmsRequest(mobile = mobile))
        if (response.errorCode != null && response.errorCode != SUCCESS_ERROR_CODE) {
            XunleiLoginResult.Failure(CODE_SMS_FAILED, response.errorDesc ?: response.errorCode.toString())
        } else {
            XunleiLoginResult.SmsSent(response.token)
        }
    } catch (io: IOException) {
        Timber.e(io, "XunleiLoginManager sendSms failed: network")
        XunleiLoginResult.Failure(CODE_NETWORK, CODE_NETWORK)
    } catch (exception: Exception) {
        Timber.e(exception, "XunleiLoginManager sendSms failed")
        XunleiLoginResult.Failure(CODE_PROTOCOL, CODE_PROTOCOL)
    }

    /**
     * 短信登录（第二步；用 [sendSms] 返回的 token）。
     *
     * @param mobile 手机号。
     * @param smsCode 短信验证码。
     * @param token 短信流程令牌。
     * @return 登录结果。
     */
    suspend fun loginWithSms(mobile: String, smsCode: String, token: String): XunleiLoginResult =
        runLogin {
            authApi.smsLogin(
                XunleiSmsLoginRequest(mobile = mobile, smsCode = smsCode, token = token)
            )
        }

    /**
     * 刷新会话（阻塞版，供 OkHttp `Authenticator` 在 IO 线程调用）。
     *
     * @return true 表示刷新成功且令牌已更新。
     */
    fun refreshSessionBlocking(): Boolean = runBlocking { refreshSession() }

    /**
     * 刷新会话：换新令牌 + 重新初始化验证码盾。
     *
     * 说明（§11.4 要点②）：刷新成功后需**重新 init captcha**，否则后续 `signin/token` 类写操作
     * 可能因验证码头过期被拒。刷新失败**不清除**已有令牌——可能只是网络抖动，
     * 由 UI 提示「登录已过期，请重新登录」并让用户决定。
     *
     * @return true 表示刷新成功。
     */
    suspend fun refreshSession(): Boolean {
        val refreshToken = tokenStore.refreshToken ?: return false
        return try {
            val response = authApi.refreshToken(refreshToken = refreshToken)
            val access = response.accessTokenText ?: return false
            tokenStore.saveTokens(access, response.refreshTokenText)
            loggedInState.value = true
            // 重新 init captcha（user_id 取新令牌的 JWT sub）。
            initializeCaptcha()
            Timber.i("XunleiLoginManager refresh ok, expiresAt=%s", tokenStore.accessTokenExpiresAtMillis())
            true
        } catch (io: IOException) {
            Timber.w(io, "XunleiLoginManager refresh failed: network")
            false
        } catch (exception: Exception) {
            Timber.w(exception, "XunleiLoginManager refresh failed")
            false
        }
    }

    /** 退出登录：清空令牌与昵称。 */
    fun logout() {
        tokenStore.clear()
        loggedInState.value = false
    }

    /**
     * 账号密码与短信共用的登录后续（captcha/init → signin/token）。
     *
     * @param call 登录接口调用。
     * @return 登录结果。
     */
    private suspend fun runLogin(call: suspend () -> XunleiLoginResponse): XunleiLoginResult = try {
        val login = call()
        when {
            login.needsReview -> {
                Timber.w("XunleiLoginManager login needs review, url=%s", login.reviewurl)
                XunleiLoginResult.NeedsReview(login.reviewurl)
            }
            login.sessionID.isNullOrBlank() -> Failure(
                CODE_LOGIN_FAILED,
                serverText(login)
            )
            else -> exchangeTokens(
                sessionID = login.sessionID,
                nickname = login.nickName,
                userId = login.userIdText
            )
        }
    } catch (io: IOException) {
        Timber.e(io, "XunleiLoginManager login failed: network")
        XunleiLoginResult.Failure(CODE_NETWORK, CODE_NETWORK)
    } catch (exception: Exception) {
        Timber.e(exception, "XunleiLoginManager login failed")
        XunleiLoginResult.Failure(CODE_PROTOCOL, CODE_PROTOCOL)
    }

    /**
     * 用 `sessionID` 换令牌并持久化（§4 ① step2）。
     *
     * @param sessionID 登录返回的会话 id。
     * @param nickname 昵称。
     * @param userId 用户 id（写入验证码盾 meta）。
     * @return 登录结果。
     */
    private suspend fun exchangeTokens(
        sessionID: String,
        nickname: String?,
        userId: String?
    ): XunleiLoginResult {
        if (!initializeCaptcha(userId)) {
            return XunleiLoginResult.Failure(CODE_CAPTCHA_FAILED, CODE_CAPTCHA_FAILED)
        }
        val tokens: XunleiTokenResponse = authApi.exchangeToken(
            XunleiExchangeTokenRequest(
                client_id = XunleiConfig.APP_CLIENT_ID,
                client_secret = XunleiConfig.APP_CLIENT_SECRET,
                signin_token = sessionID
            )
        )
        val access = tokens.accessTokenText
            ?: return XunleiLoginResult.Failure(CODE_TOKEN_FAILED, serverText(tokens))
        tokenStore.saveTokens(access, tokens.refreshTokenText)
        tokenStore.saveNickname(nickname)
        loggedInState.value = true
        Timber.i(
            "XunleiLoginManager login ok, userId=%s expiresAt=%s",
            tokenStore.userId,
            tokenStore.accessTokenExpiresAtMillis()
        )
        return XunleiLoginResult.Success(nickname)
    }

    /**
     * 初始化验证码盾并保存 `captcha_token`（§11.4 #1）。
     *
     * `captcha_sign` 用**当前毫秒时间戳**计算，并与 `meta.timestamp` 取同一值（§4 签名①）。
     *
     * @param userId 真实 user_id；登录后取登录响应的 userID，刷新时取 JWT `sub`。
     * @return true 表示拿到 captcha_token。
     */
    private suspend fun initializeCaptcha(userId: String? = tokenStore.userId): Boolean {
        val timestamp = System.currentTimeMillis()
        val request = XunleiCaptchaInitRequest(
            action = XunleiAuthApi.CAPTCHA_ACTION_SIGNIN_TOKEN,
            client_id = XunleiConfig.APP_CLIENT_ID,
            device_id = fingerprint.deviceId,
            meta = XunleiCaptchaMeta(
                client_version = XunleiConfig.APP_VERSION,
                package_name = XunleiConfig.PACKAGE_NAME,
                timestamp = timestamp.toString(),
                captcha_sign = XunleiSigning.captchaSign(
                    deviceId = fingerprint.deviceId,
                    timestampMillis = timestamp
                ),
                user_id = userId.orEmpty()
            )
        )
        val response = authApi.captchaInit(request)
        val token = response.captcha_token?.takeIf { value -> value.isNotBlank() }
        if (token == null) {
            Timber.w("XunleiLoginManager captcha init returned no token")
            return false
        }
        tokenStore.saveCaptchaToken(token)
        return true
    }

    /**
     * 组装服务端错误详情（优先原文，便于用户看到真实原因）。
     *
     * @param response 令牌 / 登录响应。
     * @return 详情文本。
     */
    private fun serverText(response: XunleiLoginResponse): String =
        response.error_description?.takeIf { value -> value.isNotBlank() }
            ?: response.error?.takeIf { value -> value.isNotBlank() }
            ?: response.errorCode?.toString().orEmpty().ifBlank { CODE_LOGIN_FAILED }

    /**
     * 组装服务端错误详情（令牌响应版）。
     *
     * @param response 令牌响应。
     * @return 详情文本。
     */
    private fun serverText(response: XunleiTokenResponse): String =
        response.error_description?.takeIf { value -> value.isNotBlank() }
            ?: response.error?.takeIf { value -> value.isNotBlank() }
            ?: CODE_TOKEN_FAILED

    private companion object {
        /** 成功的 `errorCode` 取值。 */
        const val SUCCESS_ERROR_CODE = 0

        const val CODE_LOGIN_FAILED = "XUNLEI_LOGIN_FAILED"
        const val CODE_CAPTCHA_FAILED = "XUNLEI_CAPTCHA_FAILED"
        const val CODE_TOKEN_FAILED = "XUNLEI_TOKEN_FAILED"
        const val CODE_SMS_FAILED = "XUNLEI_SMS_FAILED"
        const val CODE_NETWORK = "XUNLEI_NETWORK_ERROR"
        const val CODE_PROTOCOL = "XUNLEI_PROTOCOL_ERROR"
    }
}
