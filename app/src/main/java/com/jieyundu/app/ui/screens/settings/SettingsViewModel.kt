// 文件：SettingsViewModel.kt
// 职责：设置页数据——已支持网盘、默认并发分片数、应用版本、临时目录清理、崩溃日志导出
// 依赖：ParserRegistry、DownloadTask、BuildConfig、TempFolderManager、CrashReporter、Hilt
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jieyundu.app.BuildConfig
import com.jieyundu.app.data.settings.AppSettingsStore
import com.jieyundu.app.data.settings.DownloadDirectoryMode
import com.jieyundu.app.data.storage.DocumentTreePathResolver
import com.jieyundu.app.domain.downloader.DownloadTask
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.parser.ParserRegistry
import com.jieyundu.app.domain.transfer.TempFolderManager
import com.jieyundu.app.util.CrashReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 设置页 ViewModel。
 *
 * 说明：只读信息（支持范围、默认并发、版本）+ 临时目录手动清理
 * （阶段 13【修订 JYD-TEMP-2026-10-03】）+ 崩溃日志导出 / 清空（修复追加）。
 *
 * @param parserRegistry 解析器注册表，用于读取已接入的网盘类型。
 * @param tempFolderManager 临时目录管理器，用于手动清理残留转存文件。
 * @param appContext 应用上下文，用于定位崩溃日志目录并准备分享文件。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    parserRegistry: ParserRegistry,
    private val tempFolderManager: TempFolderManager,
    private val appSettingsStore: AppSettingsStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    /** 已接入的网盘类型（《要求.md》4.1 支持范围）。 */
    val supportedTypes: List<NetdiskType> = parserRegistry.registeredTypes()

    /** 应用版本名。 */
    val versionName: String = BuildConfig.VERSION_NAME

    /** 默认并发分片数（B2 功能②：默认 64）。 */
    val defaultChunkCount: Int = DownloadTask.DEFAULT_CHUNK_COUNT

    /** 并发分片上限（B2 功能②：上限 512）。 */
    val maxChunkCount: Int = DownloadTask.MAX_CHUNK_COUNT

    /** 并发分片下限（B2 功能②：下限 32）。 */
    val minChunkCount: Int = DownloadTask.MIN_CHUNK_COUNT

    /** 可选的并发档位（32 / 64 / 128 / 256 / 512）。 */
    val chunkCountOptions: List<Int> = DownloadTask.CHUNK_COUNT_OPTIONS

    /** 当前下载并发分片数（持久化设置）。 */
    val chunkCount: StateFlow<Int> = appSettingsStore.chunkCount

    /** 可选的「最大同时下载任务数」档位（C1：1 / 2 / 3 / 5）。 */
    val maxConcurrentTaskOptions: List<Int> = DownloadTask.MAX_CONCURRENT_TASK_OPTIONS

    /** 可选的「失败自动重试」档位（C1：0 / 1 / 3 / 5）。 */
    val maxTaskRetryOptions: List<Int> = DownloadTask.MAX_TASK_RETRY_OPTIONS

    /** 可选的「下载限速」档位，单位字节/秒（C1；0 = 不限速）。 */
    val speedLimitOptionsBytesPerSecond: List<Long> =
        DownloadTask.SPEED_LIMIT_OPTIONS_BYTES_PER_SECOND

    /** 当前最大同时下载任务数（C1）。 */
    val maxConcurrentTasks: StateFlow<Int> = appSettingsStore.maxConcurrentTasks

    /** 当前失败自动重试次数（C1）。 */
    val maxTaskRetries: StateFlow<Int> = appSettingsStore.maxTaskRetries

    /** 当前下载限速，单位字节/秒（C1；0 = 不限速）。 */
    val speedLimitBytesPerSecond: StateFlow<Long> = appSettingsStore.speedLimitBytesPerSecond

    private val _directoryState = MutableStateFlow(
        DownloadDirectoryState(
            isCustom = appSettingsStore.downloadDirectoryMode == DownloadDirectoryMode.CUSTOM,
            customPath = appSettingsStore.customDirectoryPath,
            publicFolderName = appSettingsStore.publicFolderName()
        )
    )

    /** 下载目录设置状态流。 */
    val directoryState: StateFlow<DownloadDirectoryState> = _directoryState.asStateFlow()

    /**
     * 设置下载并发分片数（B2 功能②）。
     *
     * @param count 目标档位（越界由存储层收敛到 32..512）。
     */
    fun setChunkCount(count: Int) {
        appSettingsStore.setChunkCount(count)
    }

    /**
     * 设置最大同时下载任务数（C1）。
     *
     * @param count 目标档位（越界由存储层收敛到 1..5）。
     */
    fun setMaxConcurrentTasks(count: Int) {
        appSettingsStore.setMaxConcurrentTasks(count)
    }

    /**
     * 设置失败自动重试次数（C1）。
     *
     * @param count 目标档位（越界由存储层收敛到 0..5）。
     */
    fun setMaxTaskRetries(count: Int) {
        appSettingsStore.setMaxTaskRetries(count)
    }

    /**
     * 设置下载限速（C1）。
     *
     * @param bytesPerSecond 目标限速，单位字节/秒；0 表示不限速。
     */
    fun setSpeedLimitBytesPerSecond(bytesPerSecond: Long) {
        appSettingsStore.setSpeedLimitBytesPerSecond(bytesPerSecond)
    }

    /**
     * 应用用户选定的自定义下载目录（B2 功能① / A1）。
     *
     * 说明：把 SAF 目录树 Uri 解析成真实路径后落库；解析失败时不改动设置，仅回写
     * [DownloadDirectoryState.applyFailed] 供 UI 提示。
     *
     * @param treeUri 目录选择器返回的 tree Uri。
     */
    fun applyCustomDirectory(treeUri: Uri) {
        val realPath = DocumentTreePathResolver.resolve(appContext, treeUri)
        if (realPath == null) {
            Timber.w("SettingsViewModel: unresolved tree uri %s", treeUri)
            _directoryState.value = _directoryState.value.copy(applyFailed = true)
            return
        }
        appSettingsStore.setCustomDirectory(realPath)
        _directoryState.value = DownloadDirectoryState(
            isCustom = true,
            customPath = realPath,
            publicFolderName = appSettingsStore.publicFolderName()
        )
    }

    /** 恢复默认下载目录（公共 `Download/极云渡/`，A3，无需权限）。 */
    fun resetDownloadDirectory() {
        appSettingsStore.resetDownloadDirectory()
        _directoryState.value = DownloadDirectoryState(
            isCustom = false,
            customPath = null,
            publicFolderName = appSettingsStore.publicFolderName()
        )
    }

    /** 消费「目录解析失败」提示（UI 已弹出 Toast 后调用）。 */
    fun consumeDirectoryApplyFailure() {
        _directoryState.value = _directoryState.value.copy(applyFailed = false)
    }

    private val _cleanupState = MutableStateFlow(TempCleanupState())

    /** 临时目录清理状态流。 */
    val cleanupState: StateFlow<TempCleanupState> = _cleanupState.asStateFlow()

    /**
     * 手动清理临时目录中残留的转存文件（阶段 13【修订 JYD-TEMP-2026-10-03】）。
     *
     * 说明：命中缓存 → 删除已登记文件 → 若临时目录为空则一并删除；**不会创建**目录。
     * 进行中重复点击将被忽略；[CancellationException] 原样抛出（C3）。
     */
    fun cleanupTempFiles() {
        if (_cleanupState.value.running) return
        _cleanupState.value = TempCleanupState(running = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val deleted = tempFolderManager.cleanupAll()
                _cleanupState.value = TempCleanupState(completed = true, deletedCount = deleted)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Timber.e(exception, "SettingsViewModel cleanup temp failed")
                _cleanupState.value = TempCleanupState(failed = true)
            }
        }
    }

    private val _crashState = MutableStateFlow(CrashLogState())

    /** 崩溃日志导出 / 清空的状态流。 */
    val crashState: StateFlow<CrashLogState> = _crashState.asStateFlow()

    init {
        refreshCrashLogs()
    }

    /** 刷新「是否存在崩溃日志」标记（进入设置页时调用，供按钮禁用 / 文案展示）。 */
    fun refreshCrashLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val hasLogs = CrashReporter.hasLogs(appContext)
            _crashState.value = _crashState.value.copy(hasLogs = hasLogs)
        }
    }

    /**
     * 准备导出最新一条崩溃日志。
     *
     * 说明：无日志时置 [CrashLogState.noLogs]（UI 侧据此 Toast「暂无崩溃日志」）；
     * 有日志时把可分享文件放入 [CrashLogState.shareFile]，由 UI 触发系统分享后消费。
     * 进行中重复点击将被忽略；[CancellationException] 原样抛出（C3）。
     */
    fun exportCrashLog() {
        if (_crashState.value.busy) return
        _crashState.value = _crashState.value.copy(busy = true, noLogs = false, failed = false)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = CrashReporter.prepareShareFile(appContext)
                _crashState.value = if (file == null) {
                    _crashState.value.copy(busy = false, hasLogs = false, noLogs = true)
                } else {
                    _crashState.value.copy(busy = false, hasLogs = true, shareFile = file)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Timber.e(exception, "SettingsViewModel export crash log failed")
                _crashState.value = _crashState.value.copy(busy = false, failed = true)
            }
        }
    }

    /**
     * 消费待分享文件（UI 已弹出系统分享面板后调用，避免重复分享）。
     */
    fun consumeCrashShareFile() {
        _crashState.value = _crashState.value.copy(shareFile = null)
    }

    /**
     * 清空崩溃日志。
     *
     * 说明：删除全部本地崩溃日志并刷新标记；[CancellationException] 原样抛出（C3）。
     */
    fun clearCrashLogs() {
        if (_crashState.value.busy) return
        _crashState.value = _crashState.value.copy(busy = true, noLogs = false, failed = false)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val deleted = CrashReporter.clear(appContext)
                _crashState.value = _crashState.value.copy(
                    busy = false,
                    hasLogs = false,
                    clearedCount = deleted
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Timber.e(exception, "SettingsViewModel clear crash logs failed")
                _crashState.value = _crashState.value.copy(busy = false, failed = true)
            }
        }
    }

    private val _runtimeState = MutableStateFlow(RuntimeLogState())

    /** 运行日志导出的状态流。 */
    val runtimeState: StateFlow<RuntimeLogState> = _runtimeState.asStateFlow()

    /**
     * 准备导出当前运行日志。
     *
     * 说明：把内存中最近的 Timber 日志缓冲落盘为可分享文件，用于反馈「下载失败 / 解析失败」
     * 等**被捕获**的错误现场（这类错误不触发崩溃，因此不会出现在崩溃日志里）。
     * 进行中重复点击将被忽略；[CancellationException] 原样抛出（C3）。
     */
    fun exportRuntimeLog() {
        if (_runtimeState.value.busy) return
        _runtimeState.value = _runtimeState.value.copy(busy = true, failed = false)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = CrashReporter.prepareRuntimeLogShareFile(appContext)
                _runtimeState.value = if (file == null) {
                    _runtimeState.value.copy(busy = false, failed = true)
                } else {
                    _runtimeState.value.copy(busy = false, shareFile = file)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Timber.e(exception, "SettingsViewModel export runtime log failed")
                _runtimeState.value = _runtimeState.value.copy(busy = false, failed = true)
            }
        }
    }

    /** 消费待分享的运行日志文件（UI 已弹出分享面板后调用）。 */
    fun consumeRuntimeShareFile() {
        _runtimeState.value = _runtimeState.value.copy(shareFile = null)
    }
}

/**
 * 崩溃日志导出的 UI 状态。
 *
 * @property hasLogs 当前是否存在崩溃日志。
 * @property busy 是否正在执行导出 / 清空。
 * @property noLogs 本次操作因「无日志」而未产出（UI 侧提示「暂无崩溃日志」）。
 * @property failed 本次操作失败。
 * @property shareFile 待分享的崩溃日志文件；UI 消费后置回 null。
 * @property clearedCount 最近一次清空删除的文件数量。
 */
data class CrashLogState(
    val hasLogs: Boolean = false,
    val busy: Boolean = false,
    val noLogs: Boolean = false,
    val failed: Boolean = false,
    val shareFile: File? = null,
    val clearedCount: Int = 0
)

/**
 * 运行日志导出的 UI 状态。
 *
 * @property busy 是否正在执行导出。
 * @property failed 本次导出是否失败。
 * @property shareFile 待分享的运行日志文件；UI 消费后置回 null。
 */
data class RuntimeLogState(
    val busy: Boolean = false,
    val failed: Boolean = false,
    val shareFile: File? = null
)

/**
 * 临时目录清理的 UI 状态。
 *
 * @property running 是否正在清理。
 * @property completed 是否刚刚完成一次清理。
 * @property deletedCount 本轮删除的文件数量。
 * @property failed 是否清理失败。
 */
data class TempCleanupState(
    val running: Boolean = false,
    val completed: Boolean = false,
    val deletedCount: Int = 0,
    val failed: Boolean = false
)

/**
 * 下载目录设置的 UI 状态（B2 功能①）。
 *
 * @property isCustom 是否处于「自定义目录」模式（CUSTOM）。
 * @property customPath 自定义目录的真实路径；默认模式下为 null。
 * @property publicFolderName 默认公共目录的应用专属子目录名（如「极云渡」）。
 * @property applyFailed 最近一次「更改目录」是否因无法解析而失败（UI 提示后消费）。
 */
data class DownloadDirectoryState(
    val isCustom: Boolean,
    val customPath: String?,
    val publicFolderName: String,
    val applyFailed: Boolean = false
)