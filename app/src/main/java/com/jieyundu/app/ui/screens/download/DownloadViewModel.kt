// 文件：DownloadViewModel.kt
// 职责：下载页状态与操作——任务列表（持久化进度 × 会话文件名/路径）、筛选、暂停/继续、删除本地文件
// 依赖：DownloadEngine、DownloadRepository、DownloadSessionRegistry、Hilt、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.download

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jieyundu.app.R
import com.jieyundu.app.data.repository.DownloadRepository
import com.jieyundu.app.domain.downloader.DownloadEngine
import com.jieyundu.app.domain.downloader.EngineActionResult
import com.jieyundu.app.domain.downloader.DownloadProgressState
import com.jieyundu.app.domain.downloader.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * 解析列表条目应显示的任务名（【JYD-P1B-2026-10-04】）。
 *
 * 优先级：**持久化（Room）** > 会话登记 > null。
 *
 * 存在理由（本次修复的 bug）：此前只取会话登记表，而该表是**进程内内存态**——
 * 进程重启后为空，界面便回落到「未命名任务」，等于 P1-1 把名字存进 Room 却没人读。
 * 抽成纯函数以便单测直接锁定这条优先级。
 *
 * @param persistedName Room 中的持久化任务名；空串 / null 视为无值。
 * @param sessionName 会话登记表中的任务名。
 * @return 应显示的任务名；两者皆无时返回 null（UI 回退「未命名任务」文案）。
 */
internal fun resolveTaskName(persistedName: String?, sessionName: String?): String? =
    persistedName?.takeIf { value -> value.isNotBlank() }
        ?: sessionName?.takeIf { value -> value.isNotBlank() }

/**
 * 下载页 ViewModel。
 *
 * 说明：列表数据源为「[DownloadEngine.liveProgress]（引擎实时内存快照）覆盖
 * [DownloadRepository.observeProgress]（Room 持久化进度）」后的结果，再与
 * [DownloadSessionRegistry.tasks] 合并、按当前筛选档过滤得到可展示条目；操作转发到 [DownloadEngine]。
 *
 * 为何要合并（【JYD-DLSPEED-2026-10-04】）：Room 只在「开始 / 暂停 / 完成」三刻写入且**不保存速度**，
 * 单靠它会令进度条长期停在启动值、速度恒为 `--`；引擎的实时快照（每 200ms 节流刷新）恰好补上
 * 下载中任务的实时速度与进度，且**不落库**（Owner 要求③）。
 *
 * @param downloadEngine 分片下载引擎（暂停 / 继续 / 取消 / 实时进度）。
 * @param downloadRepository 下载进度仓库。
 * @param sessionRegistry 会话内任务登记表。
 */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadEngine: DownloadEngine,
    private val downloadRepository: DownloadRepository,
    private val sessionRegistry: DownloadSessionRegistry,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _filter = MutableStateFlow(DownloadFilter.ALL)

    /** 当前筛选档。 */
    val filter: StateFlow<DownloadFilter> = _filter.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)

    /**
     * 一次性操作反馈消息（字符串资源 id）。
     *
     * 存在理由（【JYD-P1-2026-10-04】P1-2 Owner 要求「两个入口都必须有反馈，不能点了没反应」）：
     * 续传 / 重下的结果（成功或失败原因）需要让用户看见；UI 取用后调用 [consumeMessage] 清除。
     */
    val message: StateFlow<Int?> = _message.asStateFlow()

    /**
     * 消费一次性反馈消息（UI 已弹出提示后调用）。
     */
    fun consumeMessage() {
        _message.value = null
    }

    /** 下载列表状态流（按更新时间倒序，与 DAO 查询顺序一致；按当前筛选档过滤）。 */
    val items: StateFlow<List<DownloadListItem>> = combine(
        downloadRepository.observeProgress(),
        downloadEngine.liveProgress,
        sessionRegistry.tasks,
        _filter
    ) { progressList, liveProgress, tasks, currentFilter ->
        progressList
            .map { persisted ->
                val remembered = tasks[persisted.taskId]
                val merged = mergeLiveProgress(persisted, liveProgress[persisted.taskId])
                DownloadListItem(
                    progress = merged,
                    // 【JYD-P1B-2026-10-04】名字来源优先级：**持久化（Room）** > 会话登记 > null。
                    // 反了就会在进程重启后（会话登记表为空）显示「未命名任务」——这正是本次修的 bug。
                    fileName = resolveTaskName(
                        persistedName = merged.fileName,
                        sessionName = remembered?.fileName
                    ),
                    // 会话内登记优先（含文件名场景），否则回退到持久化的 savePath，
                    // 使进程重启后仍能定位并删除本地文件（【修订 JYD-SAVEPATH-2026-10-03】）。
                    savePath = remembered?.savePath ?: persisted.savePath
                )
            }
            .filter { item -> currentFilter.matches(item.progress.state) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    /**
     * 用引擎的实时快照覆盖 Room 持久化进度（【JYD-DLSPEED-2026-10-04】）。
     *
     * 规则：
     * 1. 引擎在内存中且仍处活动态（等待 / 下载中）→ 用实时快照（含速度、实时字节数）；
     * 2. 实时快照已落到终态（完成 / 失败）→ 同样采用，使「速度归零、状态更新」即时可见；
     * 3. 其余情况（进程重启后引擎已无该任务、或任务已取消并移出实时表）→ 原样保留 Room 数据。
     *
     * 注意：`savePath` 一律以 Room 记录为准（实时快照也带同一字段，但持久化值更可靠）。
     *
     * @param persisted Room 持久化的进度快照。
     * @param live 引擎内存实时快照；无对应任务时为 null。
     * @return 用于界面展示的进度快照。
     */
    private fun mergeLiveProgress(
        persisted: DownloadProgressState,
        live: DownloadProgressState?
    ): DownloadProgressState = when {
        live == null -> persisted
        live.state in DownloadEngine.ACTIVE_STATES ->
            live.copy(savePath = persisted.savePath ?: live.savePath)

        live.state == DownloadState.COMPLETED ||
            live.state == DownloadState.FAILED ->
            live.copy(savePath = persisted.savePath ?: live.savePath)

        else -> persisted
    }

    /**
     * 切换筛选档。
     *
     * @param filter 目标筛选档。
     */
    fun onFilterChange(filter: DownloadFilter) {
        _filter.value = filter
    }

    /**
     * 切换任务状态：下载中 → 暂停；已暂停 / 等待中 → 继续；终态不做处理。
     *
     * 说明：整卡点击与行尾「暂停 / 继续」按钮共用本方法（【JYD-DLSPEED2-2026-10-04】）。
     * 真正的停止 / 续传由引擎负责：[DownloadEngine.pause] 会取消该任务的协程（分片请求随即停止），
     * 已落盘的 `.part` 分片保留；[DownloadEngine.resume] 内部重新走 `start()`，
     * 由 `ChunkManager.readPartProgress` 读取已落盘长度并从断点续传。
     *
     * @param item 被操作（点击或按按钮）的列表条目。
     */
    fun toggleTask(item: DownloadListItem) {
        when (item.progress.state) {
            DownloadState.DOWNLOADING -> runEngineAction {
                downloadEngine.pause(item.progress.taskId)
            }

            DownloadState.PAUSED, DownloadState.PENDING -> runEngineAction {
                // 【JYD-P1-2026-10-04】P1-2：续传结果必须显式反馈，不能「点了没反应」。
                reportResult(downloadEngine.resume(item.progress.taskId))
            }

            DownloadState.COMPLETED,
            DownloadState.FAILED,
            DownloadState.CANCELED -> Unit
        }
    }

    /**
     * 重新下载：清空该任务的 `.part` 分片后从零开始（【JYD-P1-2026-10-04】P1-2）。
     *
     * 说明：与「继续」并列的另一条出路——分片损坏或被清理时，用户可显式选择重下。
     *
     * @param item 被重下的列表条目。
     */
    fun restartTask(item: DownloadListItem) {
        runEngineAction {
            reportResult(downloadEngine.restart(item.progress.taskId))
        }
    }

    /**
     * 把引擎的续传 / 重下结果翻译成用户可见的反馈（P1-2）。
     *
     * @param result 引擎返回结果。
     */
    private fun reportResult(result: EngineActionResult) {
        _message.value = when (result) {
            EngineActionResult.Resumed -> R.string.download_resume_started
            EngineActionResult.Restarted -> R.string.download_restart_started
            EngineActionResult.NoOp -> null
            EngineActionResult.NoCheckpoint -> R.string.download_resume_no_record
            EngineActionResult.MissingPartFiles -> R.string.download_resume_missing_parts
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
                deleteLocalFile(savePath)
            }
            downloadRepository.deleteProgress(taskId)
            sessionRegistry.forget(taskId)
        }
    }

    /**
     * 删除一个本地文件（B2 功能①：兼容「已发布到公共下载目录」的两种位置）。
     *
     * 说明：默认（A3）模式发布的成品，在 Android 10+ 上是 MediaStore 的 `content://` 记录，
     * 需经 `ContentResolver.delete` 删除；`<29` 与自定义目录则是真实文件路径，直接删文件。
     *
     * @param location 会话登记的落盘位置（`content://` Uri 或绝对路径）。
     */
    private fun deleteLocalFile(location: String) {
        if (location.startsWith(CONTENT_SCHEME)) {
            val uri = Uri.parse(location)
            val deleted = runCatching {
                appContext.contentResolver.delete(uri, null, null)
            }.getOrDefault(0)
            if (deleted <= 0) {
                Timber.e("DownloadViewModel failed to delete published file: %s", location)
            }
            return
        }
        val file = File(location)
        if (file.isFile && !file.delete()) {
            Timber.e("DownloadViewModel failed to delete local file: %s", location)
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

        /** 内容 Uri 协议前缀（已发布到 MediaStore 的成品位置）。 */
        const val CONTENT_SCHEME = "content://"
    }
}
