// 文件：XunleiAuthApi.kt
// 职责：迅雷认证主机 Retrofit 接口（验证码盾 / 登录 / 短信 / 换 token / 刷新）
// 依赖：Retrofit、XunleiAuthModels、XunleiConfig
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.xunlei

import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * 迅雷认证接口（`xluser-ssl.xunlei.com`，【JYD-XUNLEI-P1B-2026-10-08】）。
 *
 * 事实来源：《抓包事实.md》§4「登录链路」与 §6.3、§11.4 #1-#6。
 *
 * 说明：
 * - 设备头 / 验证码头由 `data/remote/XunleiAuthInterceptor` 统一注入（认证主机**不带** Bearer）；
 * - 两个登录类接口的 UA 文档不同（§6.3 注明），故用方法级 `@Headers` 固定
 *   （拦截器只在缺失时才补默认 UA，不会覆盖）；
 * - 全链路**没有 OAuth 授权码**：`sessionID` 就是换取令牌的 `signin_token`（§4 ④）。
 */
interface XunleiAuthApi {

    /**
     * 验证码盾初始化（§11.4 #1）。
     *
     * `POST https://xluser-ssl.xunlei.com/v1/shield/captcha/init`
     *
     * @param body 请求体（含 `meta.captcha_sign`）。
     * @return 含 `captcha_token` 的响应。
     */
    @POST("v1/shield/captcha/init")
    suspend fun captchaInit(@Body body: XunleiCaptchaInitRequest): XunleiCaptchaInitResponse

    /**
     * 账号密码登录（§11.4 #2；UA 依 §6.3）。
     *
     * `POST /xluser.core.login/v3/login`
     *
     * @param body **扁平**请求体：`baseLoginBody`（17 字段，§6.3）+ 账号密码业务字段，
     *   由 [XunleiLoginBody.passwordLogin] 构造。
     * @return 登录响应（成功给 `sessionID`；风控给 `1007` + `reviewurl`）。
     */
    @Headers("User-Agent: android-ok-http-client/xl-acc-sdk/version-5.1.3.513006")
    @POST("xluser.core.login/v3/login")
    suspend fun loginWithPassword(@Body body: Map<String, String>): XunleiLoginResponse

    /**
     * 发送短信验证码（§11.4 #3；UA 依 §6.3）。
     *
     * `POST /xluser.core.login/v3/sendsms`
     *
     * @param body **扁平**请求体：公共体 + `mobile` / `register`，由 [XunleiLoginBody.sendSms] 构造。
     * @return 含 `token` 的响应。
     */
    @Headers("User-Agent: android-ok-http-client/xl-acc-sdk/version-5.0.12.512000")
    @POST("xluser.core.login/v3/sendsms")
    suspend fun sendSms(@Body body: Map<String, String>): XunleiSendSmsResponse

    /**
     * 短信登录（§11.4 #4）。
     *
     * `POST /xluser.core.login/v3/smslogin`
     *
     * @param body **扁平**请求体：公共体（`creditkey` 填 sendsms 返回值）+ 短信业务字段，
     *   由 [XunleiLoginBody.smsLogin] 构造。
     * @return 登录响应。
     */
    @Headers("User-Agent: android-ok-http-client/xl-acc-sdk/version-5.0.12.512000")
    @POST("xluser.core.login/v3/smslogin")
    suspend fun smsLogin(@Body body: Map<String, String>): XunleiLoginResponse

    /**
     * 用 `signin_token`（即 `sessionID`）换 OAuth2 令牌（§11.4 #5）。
     *
     * 说明：该请求需带 `X-Captcha-Token`（拦截器自动带）。
     *
     * @param body 请求体。
     * @return `access_token` + `refresh_token`。
     */
    @POST("v1/auth/signin/token")
    suspend fun exchangeToken(@Body body: XunleiExchangeTokenRequest): XunleiTokenResponse

    /**
     * 刷新令牌（§11.4 #6，form-urlencoded）。
     *
     * `POST /v1/auth/token`
     *
     * @param grantType 固定 `refresh_token`。
     * @param clientId App 端 CLIENT_ID。
     * @param clientSecret App 端 CLIENT_SECRET。
     * @param refreshToken 当前 refresh_token。
     * @return 新的一对令牌。
     */
    @FormUrlEncoded
    @POST("v1/auth/token")
    suspend fun refreshToken(
        @Field("grant_type") grantType: String = GRANT_TYPE_REFRESH_TOKEN,
        @Field("client_id") clientId: String = XunleiConfig.APP_CLIENT_ID,
        @Field("client_secret") clientSecret: String = XunleiConfig.APP_CLIENT_SECRET,
        @Field("refresh_token") refreshToken: String
    ): XunleiTokenResponse

    companion object {
        /** 刷新用的 grant_type（§11.4 #6）。 */
        const val GRANT_TYPE_REFRESH_TOKEN = "refresh_token"

        /** `signin/token` 前的验证码盾 action（§11.4 #1 给出的示例值）。 */
        const val CAPTCHA_ACTION_SIGNIN_TOKEN = "POST:/auth/signin/token"
    }
}
