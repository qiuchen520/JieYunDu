// 文件：NetdiskLoginViewModel.kt
// 职责：网盘登录页状态与动作——WebView 登录、两级校验（预检 + 网络）、加密落库、手动兜底、登出
// 依赖：CookieStore、LoginStateManager、LoginValidator、UserAgentProvider、CookieExtractor、NetdiskType、协程
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.login

import android.os.SystemClock
import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jieyundu.app.data.remote.CookieStore
import com.jieyundu.app.data.remote.UserAgentProvider
import com.jieyundu.app.domain.login.CookieExtractor
import com.jieyundu.app.domain.login.LoginStateManager
import com.jieyundu.app.domain.login.LoginValidator
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.login.XunleiLoginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 网盘登录页 ViewModel（阶段 11，依据《WebView登录与Cookie提取实践》实现）。
 *
 * 职责：
 * - 维护 [loginTarget]：非空表示正在内嵌 WebView 登录该网盘；
 * - **两级校验**：廉价预检（Cookie 字段齐全）→ 网络校验（[LoginValidator]，确认登录成立）；
 * - 校验通过才把合并 Cookie 经 [CookieStore] **加密持久化**，并标记登录态；
 * - 轮询保护：真实校验节流 [VALIDATE_THROTTLE_MILLIS]、同凭证重试限制
 *   [SAME_CREDENTIAL_RETRY_MILLIS]、在途锁 [validating]；
 * - 手动兜底：手动保存 [submitManual]、粘贴 Cookie [submitPasted]；
 * - 登出：清 WebView Cookie + 本地存储 [logout]。
 *
 * @param cookieStore Cookie 仓库（加密持久化）。
 * @param loginStateManager 登录状态管理。
 * @param loginValidator 登录态网络校验器。
 * @param userAgentProvider UA 常量提供者。
 */
@HiltViewModel
class NetdiskLoginViewModel @Inject constructor(
    private val cookieStore: CookieStore,
    private val xunleiLoginManager: XunleiLoginManager,
    private val loginStateManager: LoginStateManager,
    private val loginValidator: LoginValidator,
    private val userAgentProvider: UserAgentProvider
) : ViewModel() {

    init {
        // 重启后回显：迅雷登录态在加密存储里，进程重启依然有效（Owner 验收项 3）。
        if (xunleiLoginManager.isLoggedIn.value) {
            loginStateManager.markLoggedIn(NetdiskType.XUNLEI)
        }
    }

    private val loginTargetState = MutableStateFlow<NetdiskType?>(null)

    /** 当前正在登录的网盘；null 表示无登录页显示。 */
    val loginTarget: StateFlow<NetdiskType?> = loginTargetState.asStateFlow()

    private val validatingState = MutableStateFlow(false)

    /** 网络校验是否在途（用于禁用「保存」按钮、阻止并发校验）。 */
    val validating: StateFlow<Boolean> = validatingState.asStateFlow()

    private val pasteDialogState = MutableStateFlow(false)

    /** 「手动粘贴 Cookie」弹窗是否显示（显示期间轮询暂停）。 */
    val pasteDialogVisible: StateFlow<Boolean> = pasteDialogState.asStateFlow()

    /** 已登录网盘集合（来自 [LoginStateManager]）。 */
    val loggedIn: StateFlow<Set<NetdiskType>> = loginStateManager.loggedIn

    /**
     * 内嵌 WebView 登录页应使用的网页 UA（**按网盘类型**）。
     *
     * 修复（【JYD-BAIDU-COOKIE-2026-10-05】）：此前无论哪家都返回夸克网页 UA，
     * 导致百度登录页以夸克 UA 打开；现改为按类型取（单一来源见
     * [UserAgentProvider.webUserAgentOf]）。
     *
     * @param type 网盘类型。
     * @return 网页 UA；该平台未抓包时返回空串（WebView 保持默认 UA）。
     */
    fun webUserAgentFor(type: NetdiskType): String = userAgentProvider.webUserAgentOf(type)

    /** 最近一次真实网络校验的时间戳（节流用）。 */
    private var lastValidateAtMillis = 0L

    /** 最近一次校验失败的凭证原文（同凭证重试用）。 */
    private var lastFailedCookie: String? = null

    /** 最近一次校验失败的时间戳（同凭证重试用）。 */
    private var lastFailedAtMillis = 0L

    /** 网络校验在途锁（与 [validating] 同源，双保险防并发）。 */
    private var validationInFlight = false

    /**
     * 打开某网盘的登录页。
     *
     * @param type 网盘类型。
     */
    fun startLogin(type: NetdiskType) {
        resetBookkeeping()
        loginTargetState.value = type
        Timber.d("NetdiskLogin startLogin %s", type)
    }

    /**
     * 原生登录（迅雷）成功后的收尾：标记登录态并关闭登录页。
     *
     * 说明：迅雷不用 Cookie 通道，登录态已由 [XunleiLoginManager] 加密持久化；
     * 此处只负责把「已登录」反映到网盘页。
     */
    fun onNativeLoginSucceeded() {
        loginStateManager.markLoggedIn(NetdiskType.XUNLEI)
        loginTargetState.value = null
        Timber.d("NetdiskLogin native login succeeded: XUNLEI")
    }

    /** 关闭登录页（返回网盘选择页）。 */
    fun closeLogin() {
        loginTargetState.value = null
        pasteDialogState.value = false
    }

    /** 打开「手动粘贴 Cookie」弹窗。 */
    fun openPasteDialog() {
        pasteDialogState.value = true
    }

    /** 关闭「手动粘贴 Cookie」弹窗。 */
    fun dismissPasteDialog() {
        pasteDialogState.value = false
    }

    /**
     * 自动轮询命中廉价预检后提交（受节流与同凭证重试约束）。
     *
     * @param type 网盘类型。
     * @param cookie 当前 WebView 合并出的候选 Cookie。
     */
    fun submitAuto(type: NetdiskType, cookie: String) {
        submit(type, cookie, manual = false)
    }

    /**
     * 手动「保存」按钮提交（绕过节流，仍受在途锁约束）。
     *
     * @param type 网盘类型。
     * @param cookie 当前 WebView 合并出的候选 Cookie。
     */
    fun submitManual(type: NetdiskType, cookie: String) {
        submit(type, cookie, manual = true)
    }

    /**
     * 提交用户粘贴的 Cookie（与手动保存走同一校验 + 落库入口）。
     *
     * @param type 网盘类型。
     * @param rawCookie 用户粘贴的原始 Cookie 串。
     */
    fun submitPasted(type: NetdiskType, rawCookie: String) {
        dismissPasteDialog()
        submit(type, rawCookie.trim(), manual = true)
    }

    /**
     * 登出某网盘：清 WebView Cookie + 本地存储 + 登录标记。
     *
     * @param type 网盘类型。
     */
    fun logout(type: NetdiskType) {
        if (type == NetdiskType.XUNLEI) {
            // 迅雷登录态不在 Cookie 里，需显式清空令牌（含 refresh_token）。
            xunleiLoginManager.logout()
        }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        cookieStore.clear()
        loginStateManager.markLoggedOut(type)
        Timber.d("NetdiskLogin logout %s", type)
    }

    /**
     * 统一提交入口：节流 / 同凭证重试 / 在途锁 → 网络校验 → 成功落库。
     *
     * @param type 网盘类型。
     * @param cookie 候选 Cookie。
     * @param manual 是否手动触发（手动绕过时间节流）。
     */
    private fun submit(type: NetdiskType, cookie: String, manual: Boolean) {
        if (cookie.isBlank() || validationInFlight) return
        val now = SystemClock.elapsedRealtime()
        if (!manual) {
            if (now - lastValidateAtMillis < VALIDATE_THROTTLE_MILLIS) return
            val failed = lastFailedCookie
            if (failed != null && failed == cookie &&
                now - lastFailedAtMillis < SAME_CREDENTIAL_RETRY_MILLIS
            ) {
                return
            }
        }
        validationInFlight = true
        validatingState.value = true
        viewModelScope.launch {
            val passed = try {
                loginValidator.validate(type, cookie)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Timber.e(error, "Netdisk login validate failed for %s", type)
                false
            }
            validationInFlight = false
            validatingState.value = false
            lastValidateAtMillis = SystemClock.elapsedRealtime()
            if (passed) {
                cookieStore.save(CookieExtractor.cookieDomainOf(type), cookie)
                loginStateManager.markLoggedIn(type)
                pasteDialogState.value = false
                loginTargetState.value = null
                Timber.d("Netdisk login success %s", type)
            } else {
                lastFailedCookie = cookie
                lastFailedAtMillis = SystemClock.elapsedRealtime()
                Timber.d("Netdisk login not ready yet %s", type)
            }
        }
    }

    /** 重置节流与重试簿记（每次进入登录页时调用）。 */
    private fun resetBookkeeping() {
        lastValidateAtMillis = 0L
        lastFailedCookie = null
        lastFailedAtMillis = 0L
        validationInFlight = false
        validatingState.value = false
        pasteDialogState.value = false
    }

    private companion object {
        /** 真实网络校验的最小间隔（《实践》§5：5000ms）。 */
        const val VALIDATE_THROTTLE_MILLIS = 5_000L

        /** 同一失败凭证的重试间隔（《实践》§5：10000ms）。 */
        const val SAME_CREDENTIAL_RETRY_MILLIS = 10_000L
    }
}