// 文件：XunleiLoginViewModel.kt
// 职责：迅雷原生登录页状态机（账号密码 / 短信两种模式、错误与风控提示）
// 依赖：XunleiLoginManager、LoginStateManager、Hilt、Compose 状态
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jieyundu.app.R
import com.jieyundu.app.data.remote.XunleiFingerprint
import com.jieyundu.app.domain.login.LoginStateManager
import com.jieyundu.app.domain.login.XunleiLoginManager
import com.jieyundu.app.domain.login.XunleiLoginResult
import com.jieyundu.app.domain.model.NetdiskType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 迅雷原生登录页 ViewModel（【JYD-XUNLEI-P1B-2026-10-08】）。
 *
 * 设计说明：
 * - 迅雷**不走网页登录**（§4 登录链路：`pan.xunlei.com` 不下发 access_token），
 *   故本页是原生表单：账号密码 / 短信两种模式，风控（`errorCode=1007`）时给出提示
 *   （WebView 安全验证属下一批，见 CHANGELOG 待办）；
 * - 页面只负责收集输入与展示结果，协议细节全在 [XunleiLoginManager]；
 * - 登录成功即写 [LoginStateManager]，使「网盘」页立即显示已登录。
 *
 * @param loginManager 迅雷登录管理器。
 * @param loginStateManager 各网盘登录状态（供网盘页展示）。
 */
@HiltViewModel
class XunleiLoginViewModel @Inject constructor(
    private val loginManager: XunleiLoginManager,
    private val loginStateManager: LoginStateManager,
    private val fingerprint: XunleiFingerprint
) : ViewModel() {

    /** 本机 deviceId（风控 WebView 需要拼进验证页 URL，§4 ⑤）。 */
    val deviceId: String get() = fingerprint.deviceId

    /** 登录模式。 */
    enum class Mode { PASSWORD, SMS }

    /**
     * 页面状态。
     *
     * @property mode 当前登录模式。
     * @property account 账号（账号密码模式）。
     * @property password 密码（账号密码模式）。
     * @property mobile 手机号（短信模式）。
     * @property smsCode 短信验证码（短信模式）。
     * @property smsToken 短信流程令牌（发短信成功后保存）。
     * @property smsCreditkey 发短信返回的 creditkey（短信登录公共体字段，§6.3）。
     * @property busy 是否有请求在途。
     * @property messageRes 提示文案资源；null 表示无提示。
     * @property serverMessage 服务端原文（透传，便于定位）；可为 null。
     * @property nickname 登录成功后的昵称。
     * @property reviewUrl 风控安全验证页地址；非空时页面切换为内嵌验证页（§4 ⑤）。
     * @property loggedIn 是否已登录成功（成功后页面可自动返回）。
     */
    data class UiState(
        val mode: Mode = Mode.PASSWORD,
        val account: String = "",
        val password: String = "",
        val mobile: String = "",
        val smsCode: String = "",
        val smsToken: String? = null,
        val smsCreditkey: String? = null,
        val busy: Boolean = false,
        val messageRes: Int? = null,
        val serverMessage: String? = null,
        val nickname: String? = null,
        val reviewUrl: String? = null,
        val loggedIn: Boolean = false
    ) {

        /** 当前模式下的主按钮是否可用。 */
        val canSubmit: Boolean
            get() = when (mode) {
                Mode.PASSWORD -> account.isNotBlank() && password.isNotBlank()
                Mode.SMS -> mobile.isNotBlank() && smsCode.isNotBlank() && !smsToken.isNullOrBlank()
            }

        /** 短信模式是否可发验证码。 */
        val canSendSms: Boolean
            get() = mobile.isNotBlank() && !busy
    }

    private val state = MutableStateFlow(UiState())

    /** 页面状态（只读流）。 */
    val uiState: StateFlow<UiState> = state.asStateFlow()

    /**
     * 切换登录模式。
     *
     * @param mode 目标模式。
     */
    fun switchMode(mode: Mode) {
        state.value = state.value.copy(mode = mode, messageRes = null, serverMessage = null)
    }

    /**
     * 更新账号。
     *
     * @param value 输入值。
     */
    fun onAccountChange(value: String) {
        state.value = state.value.copy(account = value.trim(), messageRes = null)
    }

    /**
     * 更新密码。
     *
     * @param value 输入值。
     */
    fun onPasswordChange(value: String) {
        state.value = state.value.copy(password = value, messageRes = null)
    }

    /**
     * 更新手机号。
     *
     * @param value 输入值。
     */
    fun onMobileChange(value: String) {
        state.value = state.value.copy(mobile = value.trim(), messageRes = null)
    }

    /**
     * 更新短信验证码。
     *
     * @param value 输入值。
     */
    fun onSmsCodeChange(value: String) {
        state.value = state.value.copy(smsCode = value.trim(), messageRes = null)
    }

    /** 账号密码登录。 */
    fun loginWithPassword() {
        val current = state.value
        if (!current.canSubmit || current.busy) {
            return
        }
        state.value = current.copy(
            busy = true,
            messageRes = R.string.xunlei_login_working,
            serverMessage = null
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                loginManager.loginWithPassword(current.account, current.password)
            }
            applyResult(result)
        }
    }

    /** 发送短信验证码。 */
    fun sendSms() {
        val current = state.value
        if (!current.canSendSms) {
            return
        }
        state.value = current.copy(
            busy = true,
            messageRes = R.string.xunlei_login_sending,
            serverMessage = null
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { loginManager.sendSms(current.mobile) }
            when (result) {
                is XunleiLoginResult.SmsSent -> state.value = state.value.copy(
                    busy = false,
                    smsToken = result.token,
                    smsCreditkey = result.creditkey,
                    messageRes = R.string.xunlei_login_sms_sent
                )

                is XunleiLoginResult.Failure -> state.value = state.value.copy(
                    busy = false,
                    messageRes = R.string.xunlei_login_sms_failed,
                    serverMessage = result.message
                )

                else -> state.value = state.value.copy(busy = false)
            }
        }
    }

    /** 短信登录。 */
    fun loginWithSms() {
        val current = state.value
        val token = current.smsToken
        if (!current.canSubmit || current.busy || token.isNullOrBlank()) {
            return
        }
        state.value = current.copy(
            busy = true,
            messageRes = R.string.xunlei_login_working,
            serverMessage = null
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                loginManager.loginWithSms(
                    mobile = current.mobile,
                    smsCode = current.smsCode,
                    token = token,
                    creditkey = current.smsCreditkey.orEmpty()
                )
            }
            applyResult(result)
        }
    }

    /**
     * 风控验证完成：关闭验证页并**重试上次登录**（凭据仍在状态里）。
     *
     * 说明：验证完成后服务端不再返回 1007，重试即可拿到 `sessionID`（§4 ⑤ 的回调语义）。
     */
    fun onReviewVerified() {
        state.value = state.value.copy(reviewUrl = null)
        if (state.value.mode == Mode.PASSWORD) {
            loginWithPassword()
        } else {
            loginWithSms()
        }
    }

    /** 用户关闭风控验证页：只收起页面，不改动已填凭据。 */
    fun dismissReview() {
        state.value = state.value.copy(reviewUrl = null)
    }

    /**
     * 统一收敛登录结果。
     *
     * @param result 登录结果。
     */
    private fun applyResult(result: XunleiLoginResult) {
        when (result) {
            is XunleiLoginResult.Success -> {
                loginStateManager.markLoggedIn(NetdiskType.XUNLEI)
                state.value = state.value.copy(
                    busy = false,
                    loggedIn = true,
                    nickname = result.nickname,
                    messageRes = R.string.xunlei_login_done,
                    serverMessage = null
                )
            }

            is XunleiLoginResult.NeedsReview -> {
                // 风控：切到内嵌安全验证页（§4 ⑤ 的注入配置与回调桥见 XunleiReviewWebView）。
                Timber.w("XunleiLoginViewModel needs review, url=%s", result.reviewUrl)
                state.value = state.value.copy(
                    busy = false,
                    messageRes = R.string.xunlei_login_needs_review,
                    reviewUrl = result.reviewUrl,
                    serverMessage = null
                )
            }

            is XunleiLoginResult.SmsSent -> state.value = state.value.copy(busy = false)

            is XunleiLoginResult.Failure -> state.value = state.value.copy(
                busy = false,
                messageRes = R.string.xunlei_login_failed,
                serverMessage = result.message
            )
        }
    }
}
