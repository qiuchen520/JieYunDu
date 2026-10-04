// 文件：NetdiskBrowserViewModel.kt
// 职责：网盘管理（流程 B）——浏览个人网盘目录、容量，维护路径栈与加载/错误态
// 依赖：PersonalBrowser、BrowseLevel、QuotaInfo、NetdiskType、Hilt、协程、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.login

import android.content.Context
import android.os.Environment
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jieyundu.app.R
import com.jieyundu.app.data.settings.AppSettingsStore
import com.jieyundu.app.data.settings.DownloadDirectoryMode
import com.jieyundu.app.data.storage.PublicDownloadsPublisher
import com.jieyundu.app.domain.downloader.DownloadEngine
import com.jieyundu.app.domain.downloader.DownloadState
import com.jieyundu.app.domain.downloader.DownloadTask
import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.QuotaInfo
import com.jieyundu.app.domain.parser.NetdiskServiceRouter
import com.jieyundu.app.domain.transfer.TempFolderGuard
import com.jieyundu.app.service.DownloadService
import com.jieyundu.app.ui.screens.download.DownloadSessionRegistry
import com.jieyundu.app.ui.screens.home.BrowseLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
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
 * @param downloadEngine 分片下载引擎（「下载到本地」复用既有下载链路）。
 * @param appSettingsStore 应用设置（下载目录模式 / 并发分片数）。
 * @param downloadSessionRegistry 会话内任务登记表（下载页显示文件名用）。
 * @param publicDownloadsPublisher 成品发布器（默认 A3：发布到公共下载目录）。
 * @param appContext 应用上下文（推导落盘目录）。
 */
@HiltViewModel
class NetdiskBrowserViewModel @Inject constructor(
    private val netdiskRouter: NetdiskServiceRouter,
    private val downloadEngine: DownloadEngine,
    private val appSettingsStore: AppSettingsStore,
    private val downloadSessionRegistry: DownloadSessionRegistry,
    private val publicDownloadsPublisher: PublicDownloadsPublisher,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetdiskBrowserState())

    /** 网盘管理页 UI 状态。 */
    val uiState: StateFlow<NetdiskBrowserState> = _uiState.asStateFlow()

    private val _state = MutableStateFlow(NetdiskActionState())

    /** 网盘管理页动作状态（删除确认弹窗 / 一次性提示）。 */
    val actionState: StateFlow<NetdiskActionState> = _state.asStateFlow()

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

    /**
     * 请求删除某个条目（只打开确认弹窗，**不执行**删除）。
     *
     * 【JYD-BROWSER-2026-10-04】删除自己的网盘文件不可撤销（虽然进回收站），
     * 因此必须两步：先弹确认，再由 [confirmDelete] 执行。
     *
     * @param file 目标条目。
     */
    fun requestDelete(file: FileInfo) {
        _state.value = _state.value.copy(pendingDelete = file)
    }

    /** 取消删除（关闭确认弹窗）。 */
    fun cancelDelete() {
        _state.value = _state.value.copy(pendingDelete = null)
    }

    /**
     * 执行删除（用户已在弹窗中确认）。
     *
     * 安全链路（指令要求「必须过 P0 的 TempFolderGuard 安全判断」）：
     * 用户确认 → [TempFolderGuard.mayDeleteUserInitiated]（显式 userInitiated 放行 + 审计日志）
     * → [PersonalBrowser.deletePersonalFile] → 重新列出当前目录刷新界面。
     *
     * @param file 目标条目。
     */
    fun confirmDelete(file: FileInfo) {
        val type = _uiState.value.netdiskType ?: return
        val browser = netdiskRouter.personalBrowserFor(type) ?: return
        _state.value = _state.value.copy(pendingDelete = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!TempFolderGuard.mayDeleteUserInitiated(file.fid, userInitiated = true)) {
                    Timber.w("NetdiskBrowser delete refused by guard fid=%s", file.fid)
                    _state.value = _state.value.copy(messageRes = R.string.netdisk_browser_delete_failed)
                    return@launch
                }
                val deleted = browser.deletePersonalFile(file.fid)
                _state.value = _state.value.copy(
                    messageRes = if (deleted) {
                        R.string.netdisk_browser_delete_done
                    } else {
                        R.string.netdisk_browser_delete_failed
                    }
                )
                if (deleted) {
                    reloadCurrentLevel()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Timber.e(exception, "NetdiskBrowser delete failed fid=%s", file.fid)
                _state.value = _state.value.copy(messageRes = R.string.netdisk_browser_delete_failed)
            }
        }
    }

    /**
     * 下载条目到本地（复用既有下载链路：取链 → 引擎分片下载 → 完成后发布到公共目录）。
     *
     * @param file 目标文件条目。
     */
    fun downloadToLocal(file: FileInfo) {
        if (file.isDirectory) {
            _state.value = _state.value.copy(messageRes = R.string.netdisk_browser_download_folder)
            return
        }
        val type = _uiState.value.netdiskType ?: return
        val browser = netdiskRouter.personalBrowserFor(type) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = browser.fetchPersonalDownloadUrl(file.fid)
                if (url.isNullOrBlank()) {
                    // UC 等网盘的个人文件取链尚无抓包依据 → 明确提示，不猜测参数（R3）。
                    _state.value = _state.value.copy(messageRes = R.string.netdisk_browser_download_unsupported)
                    return@launch
                }
                val taskId = UUID.randomUUID().toString()
                val directory = resolveDownloadDirectory()
                val targetFile = File(directory, file.fileName)
                val task = DownloadTask(
                    taskId = taskId,
                    url = url,
                    fileName = file.fileName,
                    fileSize = file.fileSize,
                    savePath = targetFile.absolutePath,
                    chunkCount = appSettingsStore.chunkCount.value,
                    headers = emptyMap()
                )
                downloadSessionRegistry.remember(taskId, file.fileName, task.savePath)
                downloadEngine.start(task)
                // C2：接线前台服务（保活 + 通知）；失败不影响下载本身。
                DownloadService.start(appContext, task)
                _state.value = _state.value.copy(messageRes = R.string.netdisk_browser_download_started)
                val completed = awaitDownloadCompleted(taskId)
                if (completed) {
                    publishIfNeeded(taskId, file.fileName, targetFile)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Timber.e(exception, "NetdiskBrowser download failed fid=%s", file.fid)
                _state.value = _state.value.copy(messageRes = R.string.netdisk_browser_download_failed)
            }
        }
    }

    /** 消费一次性提示消息（UI 已弹出后调用）。 */
    fun consumeMessage() {
        _state.value = _state.value.copy(messageRes = null)
    }

    /**
     * 重新列出当前目录（删除后刷新）。
     *
     * 说明：只刷新栈顶层级，路径栈本身不变。
     */
    private suspend fun reloadCurrentLevel() {
        val browsing = _uiState.value
        val type = browsing.netdiskType ?: return
        val level = browsing.currentLevel ?: return
        val browser = netdiskRouter.personalBrowserFor(type) ?: return
        val children = browser.listPersonalChildren(level.pdirFid)
        val stack = browsing.stack.dropLast(1) + level.copy(files = children)
        _uiState.value = browsing.copy(stack = stack)
    }

    /**
     * 等待任务进入终态。
     *
     * @param taskId 任务 ID。
     * @return true 表示下载完成。
     */
    private suspend fun awaitDownloadCompleted(taskId: String): Boolean {
        val finalState = downloadEngine.observe(taskId).firstOrNull { state ->
            state == DownloadState.COMPLETED ||
                state == DownloadState.FAILED ||
                state == DownloadState.CANCELED
        }
        return finalState == DownloadState.COMPLETED
    }

    /**
     * 默认目录模式下，把成品发布到公共下载目录（与首页下载同一策略）。
     *
     * @param taskId 任务 ID。
     * @param fileName 文件名。
     * @param workingFile 私有工作目录中的成品。
     */
    private suspend fun publishIfNeeded(taskId: String, fileName: String, workingFile: File) {
        if (appSettingsStore.downloadDirectoryMode != DownloadDirectoryMode.PUBLIC_DOWNLOADS) {
            return
        }
        val published = publicDownloadsPublisher.publish(
            workingFile,
            appSettingsStore.publicFolderName()
        )
        if (published == null) {
            Timber.w("NetdiskBrowser: publish to public downloads failed for %s", fileName)
            return
        }
        downloadSessionRegistry.remember(taskId, fileName, published)
        if (workingFile.isFile && !workingFile.delete()) {
            Timber.e("NetdiskBrowser: failed to delete private copy: %s", workingFile.name)
        }
    }

    /**
     * 计算本次下载的工作目录（与首页下载同策略，见 HomeViewModel.resolveDownloadDirectory）。
     *
     * @return 工作目录；自定义目录不可用时回退应用私有目录。
     */
    private fun resolveDownloadDirectory(): File {
        if (appSettingsStore.downloadDirectoryMode == DownloadDirectoryMode.CUSTOM) {
            val custom = appSettingsStore.customDirectoryPath
            if (!custom.isNullOrBlank()) {
                val dir = File(custom)
                if (dir.isDirectory || dir.mkdirs()) {
                    return dir
                }
                Timber.w("NetdiskBrowser: custom dir unavailable, fall back to private: %s", custom)
            }
        }
        return appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
    }

    /** 关闭管理页，回到网盘列表。 */
    fun close() {
        _uiState.value = NetdiskBrowserState()
        _state.value = NetdiskActionState()
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
/**
 * 网盘管理页的「动作」状态（【JYD-BROWSER-2026-10-04】删除 / 下载入口）。
 *
 * 与 [NetdiskBrowserState] 分开的原因：浏览状态描述「当前显示什么目录」，
 * 动作状态描述「正在进行的弹窗与一次性提示」，两者生命周期不同（前者随目录变化，后者随操作消费）。
 *
 * @property pendingDelete 待确认删除的条目；为 null 表示不显示确认弹窗。
 * @property messageRes 一次性提示文案（删除 / 下载结果）；UI 消费后置空。
 */
data class NetdiskActionState(
    val pendingDelete: FileInfo? = null,
    @StringRes val messageRes: Int? = null
)
