// 文件：SettingsViewModel.kt
// 职责：设置页数据——已支持网盘、默认并发分片数、应用版本、临时目录清理、崩溃日志导出
// 依赖：ParserRegistry、DownloadTask、BuildConfig、TempFolderManager、CrashReporter、Hilt
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jieyundu.app.BuildConfig
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
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    /** 已接入的网盘类型（《要求.md》4.1 支持范围）。 */
    val supportedTypes: List<NetdiskType> = parserRegistry.registeredTypes()

    /** 应用版本名。 */
    val versionName: String = BuildConfig.VERSION_NAME

    /** 默认并发分片数（4.2：默认 8）。 */
    val defaultChunkCount: Int = DownloadTask.DEFAULT_CHUNK_COUNT

    /** 并发分片上限（4.2：上限 32）。 */
    val maxChunkCount: Int = DownloadTask.MAX_CHUNK_COUNT

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