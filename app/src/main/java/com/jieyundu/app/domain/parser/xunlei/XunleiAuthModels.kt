// 文件：XunleiAuthModels.kt
// 职责：迅雷认证链路的 wire 结构（验证码盾 / 登录 / 短信 / 换 token / 刷新），字段严格取自抓包事实
// 依赖：kotlinx.serialization
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.xunlei

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * 迅雷认证链路 wire 结构（【JYD-XUNLEI-P1B-2026-10-08】）。
 *
 * 事实来源：《抓包事实.md》§4「登录链路」与 §6.3、§11.4 #1-#6。
 * 纪律（R3）：只声明文档给出的字段；未知字段一律 `TODO(用户抓包)` 标注并给安全默认值。
 */

/**
 * 验证码盾初始化请求体（§6.3 / §11.4 #1）。
 *
 * @property action 目标动作，形如 `POST:/auth/signin/token`（§11.4 #1 给出该示例）。
 * @property captcha_token 既有令牌；首次为空串。
 * @property client_id App 端 CLIENT_ID。
 * @property device_id 本机 deviceId。
 * @property meta 元信息（见 [XunleiCaptchaMeta]）。
 * @property redirect_uri 固定回调地址（§6.3；不参与发 token）。
 */
@Serializable
data class XunleiCaptchaInitRequest(
    val action: String,
    val captcha_token: String = "",
    val client_id: String,
    val device_id: String,
    val meta: XunleiCaptchaMeta,
    val redirect_uri: String = XunleiConfig.REDIRECT_URI
)

/**
 * 验证码盾元信息（§11.4 #1）。
 *
 * @property username 用户名；未登录场景为空串（§11.4 #1 原文）。
 * @property client_version App 版本。
 * @property package_name 包名。
 * @property timestamp 毫秒时间戳字符串；**必须与 captcha_sign 计算所用时间戳同一值**。
 * @property captcha_sign 见 [XunleiSigning.captchaSign]。
 * @property user_id 真实 user_id（access_token 的 JWT `sub`）；为空会拿到**降级 token**
 *   （§6.3 明确警告），故本实现对「已登录刷新」场景填真值。
 */
@Serializable
data class XunleiCaptchaMeta(
    val username: String = "",
    val client_version: String,
    val package_name: String,
    val timestamp: String,
    val captcha_sign: String,
    val user_id: String = ""
)

/**
 * 验证码盾初始化响应（§11.4 #1：响应 `captcha_token`）。
 *
 * @property captcha_token 下发的验证码盾令牌。
 * @property error 服务端错误标识（字段名未证实，透传用）。
 * @property error_description 服务端错误描述（字段名未证实，透传用）。
 */
@Serializable
data class XunleiCaptchaInitResponse(
    val captcha_token: String? = null,
    val error: String? = null,
    val error_description: String? = null
)

/**
 * 发短信响应（§11.4 #3：`creditkey / token / errorDesc`）。
 *
 * @property creditkey 短信凭据 key。
 * @property token 短信流程令牌（短信登录时回传）。
 * @property errorDesc 服务端错误描述（透传）。
 * @property errorCode 服务端错误码（失败分流用；字段名未证实，见下方 TODO）。
 */
@Serializable
data class XunleiSendSmsResponse(
    val creditkey: String? = null,
    val token: String? = null,
    val errorDesc: String? = null,
    // TODO(用户抓包): 短信接口失败时的错误码字段名（当前按通用约定读取，缺失即视为成功路径）
    val errorCode: Int? = null
)

/**
 * 登录响应（账号密码 / 短信共用；§11.4 #2/#4）。
 *
 * @property loginKey 登录 key。
 * @property sessionID 会话 id；**换 access_token 的 `signin_token` 就是它**。
 * @property nickName 昵称（登录态持久化要存的 `nickname`）。
 * @property userID 用户 id；服务端可能给字符串或数字，故用 [JsonElement] 承载，
 *   读取时统一按文本比较（与百度 `isdir` 同款处理，避免类型漂移抛序列化异常）。
 * @property errorCode 错误码；`1007` = 触发安全验证（配 `reviewurl` / `review_panel`）。
 * @property error 错误标识（字段名未证实，透传用）。
 * @property error_description 错误描述（字段名未证实，透传用）。
 * @property reviewurl 安全验证页地址（风控用）。
 * @property review_panel 风控面板数据（结构未证实，保留原始 JSON 供解析 `reviewurl`）。
 * @property verifyType 验证类型（风控时非空，§4 ①）。
 */
@Serializable
data class XunleiLoginResponse(
    val loginKey: String? = null,
    val sessionID: String? = null,
    val nickName: String? = null,
    val userID: JsonElement? = null,
    val errorCode: Int? = null,
    val error: String? = null,
    val error_description: String? = null,
    val reviewurl: String? = null,
    val review_panel: JsonElement? = null,
    val verifyType: String? = null
) {

    /** 用户 id 的文本形态（`userID` 可能是数字）。 */
    val userIdText: String?
        get() = (userID as? JsonPrimitive)?.content?.takeIf { value -> value.isNotBlank() }

    /** 是否需要走安全验证（风控）。 */
    val needsReview: Boolean
        get() = errorCode == REVIEW_ERROR_CODE || !reviewurl.isNullOrBlank() || review_panel != null

    private companion object {
        /** 触发安全验证的错误码（§4 ①：`errorCode=1007` + `reviewurl`）。 */
        const val REVIEW_ERROR_CODE = 1007
    }
}

/**
 * 换 access_token 请求体（§11.4 #5）。
 *
 * @property client_id App 端 CLIENT_ID。
 * @property client_secret App 端 CLIENT_SECRET。
 * @property provider 固定 `access_end_point_token`。
 * @property signin_token 登录响应里的 `sessionID`。
 */
@Serializable
data class XunleiExchangeTokenRequest(
    val client_id: String,
    val client_secret: String,
    val provider: String = PROVIDER_ACCESS_END_POINT_TOKEN,
    val signin_token: String
) {
    companion object {
        /** 固定 provider 取值（§11.4 #5）。 */
        const val PROVIDER_ACCESS_END_POINT_TOKEN = "access_end_point_token"
    }
}

/**
 * 令牌响应（换 token / 刷新共用；§4 ②：两个名字都读）。
 *
 * @property access_token 下划线形态。
 * @property accessToken 驼峰形态（服务端可能任一）。
 * @property refresh_token 下划线形态。
 * @property refreshToken 驼峰形态。
 * @property error 错误标识（字段名未证实，透传用）。
 * @property error_description 错误描述（字段名未证实，透传用）。
 */
@Serializable
data class XunleiTokenResponse(
    val access_token: String? = null,
    val accessToken: String? = null,
    val refresh_token: String? = null,
    val refreshToken: String? = null,
    val error: String? = null,
    val error_description: String? = null
) {

    /** access_token（兼容两种字段名）。 */
    val accessTokenText: String?
        get() = (access_token ?: accessToken)?.takeIf { value -> value.isNotBlank() }

    /** refresh_token（兼容两种字段名）。 */
    val refreshTokenText: String?
        get() = (refresh_token ?: refreshToken)?.takeIf { value -> value.isNotBlank() }
}
