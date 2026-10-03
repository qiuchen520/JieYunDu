// 文件：DownloadViewModel.kt
// 职责：下载页状态与操作——任务列表（持久化进度 × 会话文件名）、暂停 / 继续
// 依赖：DownloadEngine、DownloadRepository、DownloadSessionRegistry、Hilt、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jieyundu.app.domain.downloader.DownloadEngine
import com.jieyundu.app.domain.downloader.DownloadProgressState
import com.jieyundu.app.domain.downloader.DownloadState
import com.jieyundu.app.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 会话内「任务 ID → 文件名」登记表。
 *
 * 存在理由：Room 的下载进度表（`download_progress`）只持久化进度数值，不保存文件名；
 * 而下载列表需要展示文件名，故由本注册表在内存中登记本次会话启动的任务名。
 * 应用重启后历史任务名会缺失，此时 UI 回退为「未命名任务」占位（见 DownloadItem）。
 *
 * 线程约束：仅用 [MutableStateFlow] 做线程安全的原子替换，可在任意线程调用。
 */
@Singleton
class DownloadSessionRegistry @Inject constructor() {

    private val names = MutableStateFlow<Map<String, String>>(emptyMap())

    /** 任务 ID → 文件名的只读映射流。 */
    val fileNames: StateFlow<Map<String, String>> = names.asStateFlow()

    /**
     * 登记一个任务的显示名。
     *
     * @param taskId 任务 ID。
     * @param fileName 文件名。
     */
    fun remember(taskId: String, fileName: String) {
        names.value = names.value + (taskId to fileName)
    }
}

/**
 * 下载列表条目：持久化进度 + 会话内文件名。
 *
 * @property progress 进度快照（含状态、字节数、分片数）。
 * @property fileName 文件名；会话内未登记时为 null。
 */
data class DownloadListItem(
    val progress: DownloadProgressState,
    val fileName: String?
)

/**
 * 下载页 ViewModel。
 *
 * 说明：列表来源为 [DownloadRepository.observeProgress]（Room 持久化进度），
 * 与 [DownloadSessionRegistry.fileNames] 合并得到可展示条目；操作转发到 [DownloadEngine]。
 *
 * @param downloadEngine 分片下载引擎（暂停 / 继续）。
 * @param downloadRepository 下载进度仓库。
 * @param sessionRegistry 会话内任务名登记表。
 */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadEngine: DownloadEngine,
    private val downloadRepository: DownloadRepository,
    private val sessionRegistry: DownloadSessionRegistry
) : ViewModel() {

    /** 下载列表状态流（按更新时间倒序，与 DAO 查询顺序一致）。 */
    val items: StateFlow<List<DownloadListItem>> = combine(
        downloadRepository.observeProgress(),
        sessionRegistry.fileNames
    ) { progressList, names ->
        progressList.map { progress ->
            DownloadListItem(progress = progress, fileName = names[progress.taskId])
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    /**
     * 点击列表项时切换任务状态：下载中 → 暂停；已暂停 / 等待中 → 继续；终态不做处理。
     *
     * @param item 被点击的列表条目。
     */
    fun toggleTask(item: DownloadListItem) {
        when (item.progress.state) {
            DownloadState.DOWNLOADING -> runEngineAction {
                downloadEngine.pause(item.progress.taskId)
            }

            DownloadState.PAUSED, DownloadState.PENDING -> runEngineAction {
                downloadEngine.resume(item.progress.taskId)
            }

            DownloadState.COMPLETED,
            DownloadState.FAILED,
            DownloadState.CANCELED -> Unit
        }
    }

    /**
     * 在 IO 调度器上执行一次引擎操作，并按要求记录异常（C9 / D15）。
     *
     * 说明：本方法非 suspend；传入的挂起块内部若抛出 [CancellationException]，
     * 会被原样再次抛出（C3），其余异常记录 [Timber.e]。
     *
     * @param action 具体的引擎操作（挂起）。
     */
    private fun runEngineAction(action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Timber.e(exception, "DownloadViewModel engine action failed")
            }
        }
    }

    private companion object {
        /** 无订阅者后停止上游流的超时时间（毫秒）。 */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
