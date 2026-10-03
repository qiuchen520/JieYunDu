// 文件：DownloadViewModel.kt
// 职责：下载页状态与操作——任务列表（持久化进度 × 会话文件名/路径）、筛选、暂停/继续、删除本地文件
// 依赖：DownloadEngine、DownloadRepository、DownloadSessionRegistry、Hilt、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.download

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jieyundu.app.R
import com.jieyundu.app.data.repository.DownloadRepository
import com.jieyundu.app.domain.downloader.DownloadEngine
import com.jieyundu.app.domain.downloader.DownloadProgressState
import com.jieyundu.app.domain.downloader.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
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
 * 会话内任务登记条目（文件名 + 落盘路径）。
 *
 * @property fileName 文件名（列表展示用）。
 * @property savePath 目标文件绝对路径（删除本地文件用）；未知时为 null。
 */
data class RememberedTask(
    val fileName: String,
    val savePath: String?
)

/**
 * 会话内「任务 ID → 登记信息」表。
 *
 * 存在理由：Room 的下载进度表（`download_progress`）持久化进度数值与落盘路径，
 * 但**不保存文件名**；而下载列表需要展示文件名，故由本注册表在内存中登记本次会话启动的
 * 任务文件名（[RememberedTask.savePath] 现主要作为会话内快速路径，持久化路径见
 * `DownloadProgressState.savePath`）。应用重启后文件名会缺失，此时文件名回退为「未命名任务」
 * 占位；落盘路径已持久化，删除按钮仍可定位本地文件（见 [DownloadViewModel.deleteTask]）。
 *
 * 线程约束：仅用 [MutableStateFlow] 做线程安全的原子替换，可在任意线程调用。
 */
@Singleton
class DownloadSessionRegistry @Inject constructor() {

    private val entries = MutableStateFlow<Map<String, RememberedTask>>(emptyMap())

    /** 任务 ID → 登记信息的只读映射流。 */
    val tasks: StateFlow<Map<String, RememberedTask>> = entries.asStateFlow()

    /**
     * 登记一个任务的显示名与落盘路径。
     *
     * @param taskId 任务 ID。
     * @param fileName 文件名。
     * @param savePath 目标文件绝对路径；未知时可为 null。
     */
    fun remember(taskId: String, fileName: String, savePath: String? = null) {
        entries.value = entries.value + (taskId to RememberedTask(fileName, savePath))
    }

    /**
     * 移除一个任务的登记项（删除任务后调用）。
     *
     * @param taskId 任务 ID。
     */
    fun forget(taskId: String) {
        entries.value = entries.value - taskId
    }
}

/**
 * 下载列表筛选档（布局修订：全部 / 下载中 / 已完成）。
 *
 * @property labelRes 筛选胶囊文案资源 id。
 */
enum class DownloadFilter(@StringRes val labelRes: Int) {
    /** 全部任务。 */
    ALL(R.string.download_filter_all),

    /** 进行中（等待 / 下载中 / 已暂停）。 */
    DOWNLOADING(R.string.download_filter_downloading),

    /** 已完成。 */
    COMPLETED(R.string.download_filter_completed);

    /**
     * 判断某任务状态是否属于当前筛选档。
     *
     * @param state 任务离散状态。
     * @return true 表示应在本档展示。
     */
    fun matches(state: DownloadState): Boolean = when (this) {
        ALL -> true
        DOWNLOADING ->
            state == DownloadState.PENDING ||
                state == DownloadState.DOWNLOADING ||
                state == DownloadState.PAUSED
        COMPLETED -> state == DownloadState.COMPLETED
    }
}

/**
 * 下载列表条目：持久化进度 + 会话内文件名/落盘路径。
 *
 * @property progress 进度快照（含状态、字节数、分片数、落盘路径）。
 * @property fileName 文件名；会话内未登记时为 null。
 * @property savePath 目标文件绝对路径；优先会话登记值，否则回退 `progress.savePath`，两者皆无时为 null。
 */
data class DownloadListItem(
    val progress: DownloadProgressState,
    val fileName: String?,
    val savePath: String? = null
)

/**
 * 下载页 ViewModel。
 *
 * 说明：列表来源为 [DownloadRepository.observeProgress]（Room 持久化进度），
 * 与 [DownloadSessionRegistry.tasks] 合并后按当前筛选档过滤得到可展示条目；
 * 操作转发到 [DownloadEngine]。
 *
 * @param downloadEngine 分片下载引擎（暂停 / 继续 / 取消）。
 * @param downloadRepository 下载进度仓库。
 * @param sessionRegistry 会话内任务登记表。
 */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadEngine: DownloadEngine,
    private val downloadRepository: DownloadRepository,
    private val sessionRegistry: DownloadSessionRegistry
) : ViewModel() {

    private val _filter = MutableStateFlow(DownloadFilter.ALL)

    /** 当前筛选档。 */
    val filter: StateFlow<DownloadFilter> = _filter.asStateFlow()

    /** 下载列表状态流（按更新时间倒序，与 DAO 查询顺序一致；按当前筛选档过滤）。 */
    val items: StateFlow<List<DownloadListItem>> = combine(
        downloadRepository.observeProgress(),
        sessionRegistry.tasks,
        _filter
    ) { progressList, tasks, currentFilter ->
        progressList
            .map { progress ->
                val remembered = tasks[progress.taskId]
                DownloadListItem(
                    progress = progress,
                    fileName = remembered?.fileName,
                    // 会话内登记优先（含文件名场景），否则回退到持久化的 savePath，
                    // 使进程重启后仍能定位并删除本地文件（【修订 JYD-SAVEPATH-2026-10-03】）。
                    savePath = remembered?.savePath ?: progress.savePath
                )
            }
            .filter { item -> currentFilter.matches(item.progress.state) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    /**
     * 切换筛选档。
     *
     * @param filter 目标筛选档。
     */
    fun onFilterChange(filter: DownloadFilter) {
        _filter.value = filter
    }

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
     * 删除任务及其本地已下载文件（布局修订：列表项删除按钮）。
     *
     * 说明：
     * 1. 先调用引擎 [DownloadEngine.cancel]——运行中的任务会删除分片与目标文件并清除进度；
     * 2. 进程重启后引擎已无运行态，此步为兜底：按会话登记的 [DownloadListItem.savePath] 删除目标文件；
     * 3. 最后幂等移除进度记录与登记项（即使文件已不存在也保证列表项消失）。
     *
     * @param item 被删除的列表条目。
     */
    fun deleteTask(item: DownloadListItem) {
        val taskId = item.progress.taskId
        val savePath = item.savePath
        runEngineAction {
            downloadEngine.cancel(taskId)
            if (savePath != null) {
                val file = File(savePath)
                if (file.isFile && !file.delete()) {
                    Timber.e("DownloadViewModel failed to delete local file: %s", savePath)
                }
            }
            downloadRepository.deleteProgress(taskId)
            sessionRegistry.forget(taskId)
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
