// 文件：LoginStateManager.kt
// 职责：内存态记录各网盘的登录状态（供「网盘」页展示与后续链路判断）
// 依赖：NetdiskType、Kotlin Flow、Hilt
// 协议：AGPL-3.0

package com.jieyundu.app.domain.login

import com.jieyundu.app.domain.model.NetdiskType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 网盘登录状态管理（阶段 11）。
 *
 * 说明：本类只维护「当前会话已知哪些网盘已登录」的集合；真实登录态 Cookie 由
 * `CookieStore` 加密持久化。进程重启后 Cookie 仍在（`CookieStore` 载回），
 * 但本集合为空——由需要处按需重新判定，避免在无网络时误报。
 */
@Singleton
class LoginStateManager @Inject constructor() {

    private val loggedInTypes = MutableStateFlow<Set<NetdiskType>>(emptySet())

    /** 已登录网盘集合（只读流）。 */
    val loggedIn: StateFlow<Set<NetdiskType>> = loggedInTypes.asStateFlow()

    /**
     * 标记某网盘已登录。
     *
     * @param type 网盘类型。
     */
    fun markLoggedIn(type: NetdiskType) {
        loggedInTypes.value = loggedInTypes.value + type
    }

    /**
     * 标记某网盘已登出。
     *
     * @param type 网盘类型。
     */
    fun markLoggedOut(type: NetdiskType) {
        loggedInTypes.value = loggedInTypes.value - type
    }

    /**
     * 判断某网盘是否已登录。
     *
     * @param type 网盘类型。
     * @return true 表示已登录。
     */
    fun isLoggedIn(type: NetdiskType): Boolean = type in loggedInTypes.value
}