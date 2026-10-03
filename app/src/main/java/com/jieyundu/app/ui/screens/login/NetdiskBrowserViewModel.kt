// 文件：NetdiskBrowserViewModel.kt
// 职责：网盘管理（流程 B）——浏览个人网盘目录、容量，维护路径栈与加载/错误态
// 依赖：PersonalBrowser、BrowseLevel、QuotaInfo、NetdiskType、Hilt、协程、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jieyundu.app.R
import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.QuotaInfo
import com.jieyundu.app.domain.parser.NetdiskServiceRouter
import com.jieyundu.app.ui.screens.home.BrowseLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 网盘管理 ViewModel（流程 B，B1：夸克）。
 *
 * 职责：打开某网盘管理页 → 拉根目录 + 容量 → 点文件夹压栈 → 返回上一级弹栈。
 * 个人网盘浏览不需要 pwdId/stoken，只维护「目录 fid 路径栈」。
 *
 * 说明：当前夸克与 UC 均有个人网盘浏览实现；其余类型给出「开发中」提示。
 *
 * @param netdiskRouter 网盘能力路由器（B2：按 type 取对应个人网盘浏览器）。
 */
@HiltViewModel
class NetdiskBrowserViewModel @Inject constructor(
    private val netdiskRouter: NetdiskServiceRouter
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetdiskBrowserState())

    /** 网盘管理页 UI 状态。 */
    val uiState: StateFlow<NetdiskBrowserState> = _uiState.asStateFlow()

    /**
     * 打开某网盘的管理页。
     *
     * 说明：仅当该网盘有 [PersonalBrowser] 实现时才真正拉取；否则置
     * [NetdiskBrowserState.unsupported] 由 UI 提示「开发中」。
     *
     * @param type 网盘类型。
     */
    fun open(type: NetdiskType) {
        // 按网盘类型取对应的个人网盘浏览器（B2：多网盘路由，替代单例硬绑）。
        val browser = netdiskRouter.personalBrowserFor(type)
        if (browser == null) {
            _uiState.value = NetdiskBrowserState(
                open = true,
                netdiskType = type,
                unsupported = true
            )
            return
        }
        _uiState.value = NetdiskBrowserState(
            open = true,
            netdiskType = type,
            isLoading = true
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = browser.listPersonalChildren(ROOT_PDIR_FID)
                val quota = browser.fetchQuota()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    stack = listOf(BrowseLevel(pdirFid = ROOT_PDIR_FID, name = "", files = root)),
                    quota = quota
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Timber.e(exception, "NetdiskBrowser open failed")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorRes = R.string.netdisk_browser_failed
                )
            }
        }
    }

    /** 关闭管理页，回到网盘列表。 */
    fun close() {
        _uiState.value = NetdiskBrowserState()
    }

    /**
     * 进入某个文件夹（压栈并拉取其子项）。
     *
     * @param folder 被点击的文件夹条目。
     */
    fun openFolder(folder: FileInfo) {
        if (!folder.isDirectory) return
        if (_uiState.value.isLoading) return
        // 按当前网盘类型取个人网盘浏览器（B2：多网盘路由）。
        val type = _uiState.value.netdiskType ?: return
        val browser = netdiskRouter.personalBrowserFor(type) ?: return
        _uiState.value = _uiState.value.copy(isLoading = true, errorRes = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val children = browser.listPersonalChildren(folder.fid)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    stack = _uiState.value.stack + BrowseLevel(
                        pdirFid = folder.fid,
                        name = folder.fileName,
                        files = children
                    )
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Timber.e(exception, "NetdiskBrowser openFolder failed")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorRes = R.string.netdisk_browser_failed
                )
            }
        }
    }

    /** 返回上一级目录（弹栈；已在根目录时不动作）。 */
    fun navigateUp() {
        val stack = _uiState.value.stack
        if (stack.size <= 1) return
        _uiState.value = _uiState.value.copy(stack = stack.dropLast(1), errorRes = null)
    }

    private companion object {
        /** 根目录 pdir_fid（夸克实测为 "0"）。 */
        const val ROOT_PDIR_FID = "0"
    }
}

/**
 * 网盘管理页 UI 状态。
 *
 * @property open 是否处于管理页（false 表示显示网盘列表）。
 * @property netdiskType 当前管理的网盘类型。
 * @property stack 目录路径栈；栈底为根目录，栈顶为当前目录。
 * @property quota 容量信息；未取到为 null。
 * @property isLoading 是否正在加载目录。
 * @property errorRes 加载失败文案；无错误为 null。
 * @property unsupported 该网盘是否尚无管理实现（UI 提示「开发中」）。
 */
data class NetdiskBrowserState(
    val open: Boolean = false,
    val netdiskType: NetdiskType? = null,
    val stack: List<BrowseLevel> = emptyList(),
    val quota: QuotaInfo? = null,
    val isLoading: Boolean = false,
    @StringRes val errorRes: Int? = null,
    val unsupported: Boolean = false
) {
    /** 当前浏览的目录层；未加载时为 null。 */
    val currentLevel: BrowseLevel?
        get() = stack.lastOrNull()

    /** 是否可以返回上一级（栈深大于 1）。 */
    val canNavigateUp: Boolean
        get() = stack.size > 1
}