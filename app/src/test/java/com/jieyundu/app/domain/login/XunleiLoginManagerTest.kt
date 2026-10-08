// 文件：XunleiLoginManagerTest.kt
// 职责：迅雷登录链路与刷新的单元测试（调用顺序 / user_id 与时间戳一致性 / 风控分流 / 令牌持久化）
// 依赖：XunleiLoginManager、XunleiAuthApi、XunleiTokenStore、XunleiFingerprint、kotlin.test
// 协议：AGPL-3.0

package com.jieyundu.app.domain.login

import com.jieyundu.app.data.remote.XunleiFingerprint
import com.jieyundu.app.data.remote.XunleiTokenStore
import com.jieyundu.app.domain.parser.xunlei.XunleiAuthApi
import com.jieyundu.app.domain.parser.xunlei.XunleiCaptchaInitRequest
import com.jieyundu.app.domain.parser.xunlei.XunleiCaptchaInitResponse
import com.jieyundu.app.domain.parser.xunlei.XunleiConfig
import com.jieyundu.app.domain.parser.xunlei.XunleiExchangeTokenRequest
import com.jieyundu.app.domain.parser.xunlei.XunleiLoginRequest
import com.jieyundu.app.domain.parser.xunlei.XunleiLoginResponse
import com.jieyundu.app.domain.parser.xunlei.XunleiSendSmsRequest
import com.jieyundu.app.domain.parser.xunlei.XunleiSendSmsResponse
import com.jieyundu.app.domain.parser.xunlei.XunleiSigning
import com.jieyundu.app.domain.parser.xunlei.XunleiSmsLoginRequest
import com.jieyundu.app.domain.parser.xunlei.XunleiTokenResponse
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive

/**
 * [XunleiLoginManager] 的单元测试（【JYD-XUNLEI-P1B-2026-10-08】）。
 *
 * 覆盖点（依据《抓包事实.md》§4「登录链路」与 §11.4 #1-#6）：
 * 1. 调用顺序必须是 `login/smslogin → captcha/init → signin/token`（文档注明 `meta.user_id`
 *    必须真实，否则拿降级 token，故 init 必须在登录之后）；
 * 2. `captcha/init` 的 `meta.user_id` 取登录响应的 `userID`，且
 *    `meta.captcha_sign` 必须与 `meta.timestamp` **同一时间戳**算出（逐字段复算验证）；
 * 3. 登录请求体严格照 §6.3（`userName/passWord/verifyKey/verifyCode/isMd5Pwd`）；
 * 4. 风控 `errorCode=1007` + `reviewurl` → `NeedsReview`，且**不写令牌**；
 * 5. 缺 `sessionID` → 失败；captcha 拿不到 token → 失败；
 * 6. 令牌字段名兼容 `access_token` / `accessToken`；
 * 7. 刷新成功 → 更新令牌并**重新 init captcha**；刷新失败 → 返回 false 但**保留**旧令牌。
 *
 * 说明：测试用 `XunleiTokenStore(null)` / `XunleiFingerprint(null)`（无加密存储时退化为内存），
 * 因此可在纯 JVM 下运行；代码内不出现中文字面量（C5）。
 */
class XunleiLoginManagerTest {

    /** 账号密码登录成功：顺序正确、令牌与昵称落库、user_id 与签名时间戳一致。 */
    @Test
    fun loginWithPassword_runsCaptchaThenExchangeAndPersistsTokens() = runTest {
        val api = FakeAuthApi(loginResponse = loginResponse(sessionID = "sid-1", userID = "777"))
        val store = newStore()
        val manager = newManager(api, store)

        val result = manager.loginWithPassword("user-a", "pwd-b")

        assertTrue(result is XunleiLoginResult.Success, "expected success, got $result")
        assertEquals(listOf("login", "captcha", "exchange"), api.calls)
        // 登录请求体照 §6.3。
        assertEquals("user-a", api.lastLoginBody?.userName)
        assertEquals("pwd-b", api.lastLoginBody?.passWord)
        assertEquals("0", api.lastLoginBody?.isMd5Pwd)
        assertEquals("", api.lastLoginBody?.verifyKey)
        // captcha/init 的 meta。
        val captcha = assertNotNull(api.lastCaptchaBody)
        assertEquals("777", captcha.meta.user_id, "meta.user_id must be the real user id")
        assertEquals(XunleiAuthApi.CAPTCHA_ACTION_SIGNIN_TOKEN, captcha.action)
        assertEquals(XunleiConfig.APP_CLIENT_ID, captcha.client_id)
        assertEquals(api.fingerprintDeviceId, captcha.device_id)
        // 关键：签名用的时间戳与 meta.timestamp 必须是同一个值。
        assertEquals(
            captcha.meta.captcha_sign,
            XunleiSigning.captchaSign(
                deviceId = captcha.device_id,
                timestampMillis = captcha.meta.timestamp.toLong()
            )
        )
        // 令牌与昵称落库。
        assertEquals("access-1", store.accessToken)
        assertEquals("refresh-1", store.refreshToken)
        assertEquals("nick-a", store.nickname)
        assertTrue(store.isLoggedIn)
    }

    /** 风控（1007 + reviewurl）：返回 NeedsReview，不写任何令牌。 */
    @Test
    fun loginWithPassword_whenReviewRequired_returnsNeedsReviewAndKeepsStoreEmpty() = runTest {
        val api = FakeAuthApi(
            loginResponse = loginResponse(
                sessionID = null,
                errorCode = 1007,
                reviewUrl = "https://verify.example/panel"
            )
        )
        val store = newStore()
        val result = newManager(api, store).loginWithPassword("u", "p")

        assertTrue(result is XunleiLoginResult.NeedsReview)
        assertEquals("https://verify.example/panel", (result as XunleiLoginResult.NeedsReview).reviewUrl)
        assertEquals(listOf("login"), api.calls, "must not touch captcha/token when review is required")
        assertFalse(store.isLoggedIn)
    }

    /** 缺 sessionID 且无风控标记：登录失败。 */
    @Test
    fun loginWithPassword_withoutSessionId_fails() = runTest {
        val api = FakeAuthApi(loginResponse = loginResponse(sessionID = null, errorDescription = "bad"))
        val result = newManager(api, newStore()).loginWithPassword("u", "p")
        assertTrue(result is XunleiLoginResult.Failure)
        assertEquals("bad", (result as XunleiLoginResult.Failure).message)
    }

    /** captcha 未下发 token：登录失败，且不写令牌。 */
    @Test
    fun loginWithPassword_whenCaptchaTokenMissing_fails() = runTest {
        val api = FakeAuthApi(
            loginResponse = loginResponse(sessionID = "sid", userID = "1"),
            captchaToken = null
        )
        val store = newStore()
        val result = newManager(api, store).loginWithPassword("u", "p")
        assertTrue(result is XunleiLoginResult.Failure)
        assertEquals("XUNLEI_CAPTCHA_FAILED", (result as XunleiLoginResult.Failure).code)
        assertFalse(store.isLoggedIn)
    }

    /** 短信：发短信返回 token；短信登录走同一套 captcha + exchange。 */
    @Test
    fun smsFlow_sendsSmsThenLogsIn() = runTest {
        val api = FakeAuthApi(
            smsResponse = XunleiSendSmsResponse(creditkey = "ck", token = "sms-token"),
            loginResponse = loginResponse(sessionID = "sid-2", userID = "888")
        )
        val store = newStore()
        val manager = newManager(api, store)

        val sent = manager.sendSms("13800000000")
        assertTrue(sent is XunleiLoginResult.SmsSent)
        assertEquals("sms-token", (sent as XunleiLoginResult.SmsSent).token)
        assertEquals("13800000000", api.lastSmsBody?.mobile)
        assertEquals("0", api.lastSmsBody?.register)

        val login = manager.loginWithSms("13800000000", "123456", "sms-token")
        assertTrue(login is XunleiLoginResult.Success)
        assertEquals(listOf("sms", "smslogin", "captcha", "exchange"), api.calls)
        assertEquals("sms-token", api.lastSmsLoginBody?.token)
        assertEquals("123456", api.lastSmsLoginBody?.smsCode)
        assertTrue(store.isLoggedIn)
    }

    /** 令牌字段名兼容：服务端给驼峰时同样能取到。 */
    @Test
    fun loginWithPassword_acceptsCamelCaseTokenFields() = runTest {
        val api = FakeAuthApi(
            loginResponse = loginResponse(sessionID = "sid", userID = "1"),
            tokenResponse = XunleiTokenResponse(accessToken = "camel-a", refreshToken = "camel-r")
        )
        val store = newStore()
        assertTrue(newManager(api, store).loginWithPassword("u", "p") is XunleiLoginResult.Success)
        assertEquals("camel-a", store.accessToken)
        assertEquals("camel-r", store.refreshToken)
    }

    /** 刷新成功：换新令牌并重新 init captcha。 */
    @Test
    fun refreshSession_updatesTokensAndReinitializesCaptcha() = runTest {
        val api = FakeAuthApi(
            tokenResponse = XunleiTokenResponse(access_token = "new-a", refresh_token = "new-r")
        )
        val store = newStore().apply { saveTokens("old-a", "old-r") }
        val manager = newManager(api, store)

        assertTrue(manager.refreshSession())

        assertEquals("new-a", store.accessToken)
        assertEquals("new-r", store.refreshToken)
        assertEquals(listOf("refresh", "captcha"), api.calls)
    }

    /** 刷新失败（网络）：返回 false，且**保留**旧令牌（可能只是抖动，不强制重登）。 */
    @Test
    fun refreshSession_whenNetworkFails_keepsExistingTokens() = runTest {
        val api = FakeAuthApi(throwOnRefresh = IOException("boom"))
        val store = newStore().apply { saveTokens("old-a", "old-r") }
        val manager = newManager(api, store)

        assertFalse(manager.refreshSession())
        assertEquals("old-a", store.accessToken)
        assertEquals("old-r", store.refreshToken)
    }

    /** 未登录时刷新：直接 false，不发请求。 */
    @Test
    fun refreshSession_withoutRefreshToken_doesNothing() = runTest {
        val api = FakeAuthApi()
        assertFalse(newManager(api, newStore()).refreshSession())
        assertTrue(api.calls.isEmpty())
    }

    /** 登出清空令牌与昵称。 */
    @Test
    fun logout_clearsTokens() = runTest {
        val store = newStore().apply {
            saveTokens("a", "r")
            saveNickname("nick")
        }
        newManager(FakeAuthApi(), store).logout()
        assertFalse(store.isLoggedIn)
        assertEquals(null, store.nickname)
    }

    /**
     * 构造内存态令牌存储。
     *
     * @return 测试用存储。
     */
    private fun newStore(): XunleiTokenStore = XunleiTokenStore(prefs = null)

    /**
     * 构造被测管理器（指纹用内存态随机值）。
     *
     * @param api 假认证接口。
     * @param store 令牌存储。
     * @return 登录管理器。
     */
    private fun newManager(api: FakeAuthApi, store: XunleiTokenStore): XunleiLoginManager =
        XunleiLoginManager(
            authApi = api,
            tokenStore = store,
            fingerprint = XunleiFingerprint(prefs = null).also { api.fingerprintDeviceId = it.deviceId }
        )

    /**
     * 构造登录响应。
     *
     * @param sessionID 会话 id。
     * @param userID 用户 id（数字形态，模拟服务端）。
     * @param errorCode 错误码。
     * @param errorDescription 错误描述。
     * @param reviewUrl 安全验证地址。
     * @return 响应对象。
     */
    private fun loginResponse(
        sessionID: String?,
        userID: String? = null,
        errorCode: Int? = null,
        errorDescription: String? = null,
        reviewUrl: String? = null
    ): XunleiLoginResponse = XunleiLoginResponse(
        loginKey = "lk",
        sessionID = sessionID,
        nickName = "nick-a",
        userID = userID?.let { value -> JsonPrimitive(value) },
        errorCode = errorCode,
        error_description = errorDescription,
        reviewurl = reviewUrl
    )

    /**
     * 假认证接口（记录调用顺序与请求体）。
     *
     * @property loginResponse 登录接口返回。
     * @property smsResponse 发短信返回。
     * @property tokenResponse 换 token / 刷新返回。
     * @property captchaToken 验证码盾下发的 token；null 表示不下发。
     * @property throwOnRefresh 刷新时抛出的异常。
     */
    private class FakeAuthApi(
        private val loginResponse: XunleiLoginResponse = XunleiLoginResponse(sessionID = "sid-default"),
        private val smsResponse: XunleiSendSmsResponse = XunleiSendSmsResponse(token = "sms-token"),
        private val tokenResponse: XunleiTokenResponse =
            XunleiTokenResponse(access_token = "access-1", refresh_token = "refresh-1"),
        private val captchaToken: String? = "captcha-1",
        private val throwOnRefresh: IOException? = null
    ) : XunleiAuthApi {

        val calls = mutableListOf<String>()
        var lastLoginBody: XunleiLoginRequest? = null
        var lastSmsLoginBody: XunleiSmsLoginRequest? = null
        var lastSmsBody: XunleiSendSmsRequest? = null
        var lastCaptchaBody: XunleiCaptchaInitRequest? = null
        var fingerprintDeviceId: String = ""

        override suspend fun captchaInit(body: XunleiCaptchaInitRequest): XunleiCaptchaInitResponse {
            calls += "captcha"
            lastCaptchaBody = body
            return XunleiCaptchaInitResponse(captcha_token = captchaToken)
        }

        override suspend fun loginWithPassword(body: XunleiLoginRequest): XunleiLoginResponse {
            calls += "login"
            lastLoginBody = body
            return loginResponse
        }

        override suspend fun sendSms(body: XunleiSendSmsRequest): XunleiSendSmsResponse {
            calls += "sms"
            lastSmsBody = body
            return smsResponse
        }

        override suspend fun smsLogin(body: XunleiSmsLoginRequest): XunleiLoginResponse {
            calls += "smslogin"
            lastSmsLoginBody = body
            return loginResponse
        }

        override suspend fun exchangeToken(body: XunleiExchangeTokenRequest): XunleiTokenResponse {
            calls += "exchange"
            return tokenResponse
        }

        override suspend fun refreshToken(
            grantType: String,
            clientId: String,
            clientSecret: String,
            refreshToken: String
        ): XunleiTokenResponse {
            calls += "refresh"
            throwOnRefresh?.let { error -> throw error }
            return tokenResponse
        }
    }
}
