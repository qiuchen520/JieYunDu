// 文件：SettingsViewModel.kt
// 职责：设置页数据——已支持网盘、默认并发分片数、应用版本
// 依赖：ParserRegistry、DownloadTask、BuildConfig、Hilt
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jieyundu.app.BuildConfig
import com.jieyundu.app.domain.downloader.DownloadTask
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.parser.ParserRegistry
import com.jieyundu.app.domain.transfer.TempFolderManager
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
 * 设置页 ViewModel。
 *
 * 说明：只读信息（支持范围、默认并发、版本）+ 临时目录手动清理
 * （阶段 13【修订 JYD-TEMP-2026-10-03】：清理转存产生的 `.极云渡临时` 残留文件）。
 *
 * @param parserRegistry 解析器注册表，用于读取已接入的网盘类型。
 * @param tempFolderManager 临时目录管理器，用于手动清理残留转存文件。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    parserRegistry: ParserRegistry,
    private val tempFolderManager: TempFolderManager
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
}

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