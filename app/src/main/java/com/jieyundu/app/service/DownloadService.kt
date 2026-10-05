// 文件：DownloadService.kt
// 职责：下载前台服务，托管下载引擎、持有唤醒锁并把（可多任务的）进度同步到通知栏
// 依赖：DownloadEngine、NotificationHelper、DownloadWakeLockManager、AppSettingsStore、DownloadTask
// 协议：AGPL-3.0

package com.jieyundu.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jieyundu.app.data.settings.AppSettingsStore
import com.jieyundu.app.domain.downloader.DownloadEngine
import com.jieyundu.app.domain.downloader.DownloadState
import com.jieyundu.app.domain.downloader.DownloadTask
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 下载前台服务（C2「后台保活与通知」）。
 *
 * **多任务并发语义（【JYD-DEBT1-2026-10-05】修复）**：本服务同时跟踪**多个**下载任务。
 * 修复前的缺陷：服务只观察「最近一次启动的任务」，且任一任务到达终态就 `stopSelf` +
 * 释放唤醒锁——当「同时下载任务数 > 1」时，第一个任务下完会把前台保活与唤醒锁一起收掉，
 * 其余任务在后台裸奔（且通知只反映一个任务）。现在：
 * - 每个任务一个观察协程，互不干扰；
 * - 前台状态与唤醒锁**在所有被跟踪任务全部到达终态后才收尾**；
 * - 通知为**聚合通知**：显示最近进展的任务，并在多于一个任务时附「另有 N 个任务」。
 *
 * 职责：
 * 1. 以前台服务身份启动，保证后台下载不被系统随意回收；
 * 2. 调用 [DownloadEngine.start] 执行下载（引擎自身按「最大同时任务数」排队）；
 * 3. 按设置（[AppSettingsStore.downloadNotificationEnabled]）决定是否发布通知；
 * 4. 按设置（[AppSettingsStore.keepDownloadingOnLock]）在下载期间持有 `PARTIAL_WAKE_LOCK`。
 */
@AndroidEntryPoint
class DownloadService : Service() {

    /** 下载引擎（由 Hilt 注入）。 */
    @Inject
    lateinit var engine: DownloadEngine

    /** 通知构建工具（由 Hilt 注入）。 */
    @Inject
    lateinit var notificationHelper: NotificationHelper

    /** 唤醒锁管理器（由 Hilt 注入，C2 第 1 条）。 */
    @Inject
    lateinit var wakeLockManager: DownloadWakeLockManager

    /** 应用设置（C2：保活开关 / 通知开关）。 */
    @Inject
    lateinit var settings: AppSettingsStore

    /** 服务自有作用域，销毁时统一取消。 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 正在跟踪的任务：taskId → 观察协程。
     *
     * 线程约束：仅在主线程（`onStartCommand` 与 `Dispatchers.Main.immediate` 的协程）读写，
     * 故无需额外同步。
     */
    private val observeJobs = mutableMapOf<String, Job>()

    /** 正在跟踪的任务名：taskId → 文件名（通知聚合展示用）。 */
    private val taskNames = mutableMapOf<String, String>()

    /** 是否已持有唤醒锁（与释放严格配对）。 */
    private var wakeLockHeld: Boolean = false

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val task = intent?.toDownloadTask()
        if (task == null) {
            Timber.e("DownloadService started without a valid task")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        taskNames[task.taskId] = task.fileName
        // 每次 startForegroundService 都必须进前台（系统 5 秒时限），多任务时只是刷新通知。
        if (!startForegroundWithNotification(task.fileName, INITIAL_PERCENT)) {
            Timber.w("DownloadService startForeground rejected; keep running without foreground")
        }
        acquireWakeLockIfNeeded()
        observeJobs.remove(task.taskId)?.cancel()
        observeJobs[task.taskId] = serviceScope.launch { observeTask(task) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observeJobs.values.forEach { job -> job.cancel() }
        observeJobs.clear()
        taskNames.clear()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    /**
     * 跟踪单个任务：启动下载并把进度写入通知，直到该任务到达终态。
     *
     * @param task 运行态任务。
     */
    private suspend fun observeTask(task: DownloadTask) {
        try {
            engine.start(task)
            // 实时快照在下载中不断刷新、终态也会发布；60 秒无更新视为引擎已不再持有该任务。
            engine.liveProgress
                .map { live -> live[task.taskId] }
                .filterNotNull()
                .timeout(LIVE_PROGRESS_TIMEOUT_MILLIS.milliseconds)
                .collect { progress -> publishProgressNotification(task, progress.percent, progress.state) }
        } catch (cancellation: CancellationException) {
            // C3：服务销毁 / 任务替换导致的取消，原样抛出
            throw cancellation
        } catch (exception: Exception) {
            Timber.e(exception, "DownloadService observeTask failed for task %s", task.taskId)
        } finally {
            finishTask(task.taskId)
        }
    }

    /**
     * 按任务状态刷新通知。
     *
     * 说明：任务进入暂停 / 失败 / 取消时**不撤下**通知——同一个通知 ID 服务于所有任务，
     * 撤下会连带抹掉其它仍在进行的任务的进度；统一在「全部任务收尾」时清理。
     *
     * @param task 运行态任务。
     * @param percent 当前进度百分比。
     * @param state 当前状态。
     */
    private fun publishProgressNotification(task: DownloadTask, percent: Int, state: DownloadState) {
        val enabled = settings.downloadNotificationEnabled.value
        when (state) {
            DownloadState.COMPLETED -> notificationHelper.notify(
                NotificationHelper.NOTIFICATION_ID_PROGRESS,
                notificationHelper.buildCompletedNotification(task.fileName),
                enabled
            )

            DownloadState.DOWNLOADING -> notificationHelper.notify(
                NotificationHelper.NOTIFICATION_ID_PROGRESS,
                notificationHelper.buildProgressNotification(
                    task.fileName,
                    percent,
                    extraTaskCount = pendingTaskCount()
                ),
                enabled
            )

            DownloadState.PENDING,
            DownloadState.PAUSED,
            DownloadState.FAILED,
            DownloadState.CANCELED -> Unit
        }
    }

    /**
     * 收尾某个任务：注销其观察协程；**全部任务结束**时撤下前台、停止服务并释放唤醒锁。
     *
     * 幂等：同一任务重复调用只有第一次生效（避免取消自身触发的 `finally` 造成重复收尾）。
     *
     * @param taskId 任务 ID。
     */
    private fun finishTask(taskId: String) {
        val wasActive = observeJobs.remove(taskId) != null
        taskNames.remove(taskId)
        if (!wasActive) {
            return
        }
        Timber.i("DownloadService task finished: %s, remaining=%d", taskId, observeJobs.size)
        if (observeJobs.isEmpty()) {
            notificationHelper.cancel(NotificationHelper.NOTIFICATION_ID_PROGRESS)
            stopForegroundCompat()
            stopSelf()
            releaseWakeLock()
        }
    }

    /**
     * 计算「除最新进展任务之外」的任务数（用于聚合通知文案）。
     *
     * @return 附加任务数；只有一个任务时返回 0。
     */
    private fun pendingTaskCount(): Int = (observeJobs.size - 1).coerceAtLeast(0)

    /**
     * 以 dataSync 类型进入前台并挂出/刷新通知（C2 第 2 条：按设置决定通知详略）。
     *
     * @param fileName 文件名。
     * @param percent 初始进度。
     * @return true 表示成功进入前台；false 表示被系统拒绝（含后台启动限制）。
     */
    private fun startForegroundWithNotification(fileName: String, percent: Int): Boolean {
        val notification = if (settings.downloadNotificationEnabled.value) {
            notificationHelper.buildProgressNotification(
                fileName,
                percent,
                extraTaskCount = pendingTaskCount()
            )
        } else {
            // 关闭进度通知后仍必须有前台通知（系统硬性要求），退化为静默无进度通知。
            notificationHelper.buildSilentForegroundNotification()
        }
        return try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
            true
        } catch (exception: RuntimeException) {
            // Android 12+ 后台启动前台服务限制 / 机型差异：不能崩溃，降级为普通后台服务。
            Timber.w(exception, "DownloadService startForeground failed")
            false
        }
    }

    /** 撤下前台状态但保留通知。 */
    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
    }

    /**
     * 按设置申请唤醒锁（C2 第 1 条：锁屏后保持下载）。
     *
     * 说明：唤醒锁由本服务统一持有一次，**所有任务结束**时才释放（[finishTask]）。
     */
    private fun acquireWakeLockIfNeeded() {
        if (wakeLockHeld) {
            return
        }
        if (!settings.keepDownloadingOnLock.value) {
            Timber.i("DownloadService wake lock disabled by setting")
            return
        }
        wakeLockManager.acquire()
        wakeLockHeld = true
    }

    /** 释放唤醒锁（幂等；与 [acquireWakeLockIfNeeded] 严格配对）。 */
    private fun releaseWakeLock() {
        if (!wakeLockHeld) {
            return
        }
        wakeLockManager.release()
        wakeLockHeld = false
    }

    /**
     * 从 Intent 中还原任务描述。
     *
     * @return 任务对象；参数缺失时返回 null。
     */
    private fun Intent.toDownloadTask(): DownloadTask? {
        val taskId = getStringExtra(EXTRA_TASK_ID) ?: return null
        val url = getStringExtra(EXTRA_URL) ?: return null
        val fileName = getStringExtra(EXTRA_FILE_NAME) ?: return null
        val savePath = getStringExtra(EXTRA_SAVE_PATH) ?: return null
        val fileSize = getLongExtra(EXTRA_FILE_SIZE, DownloadTask.DEFAULT_FILE_SIZE)
        val chunkCount = getIntExtra(EXTRA_CHUNK_COUNT, DownloadTask.DEFAULT_CHUNK_COUNT)
        return DownloadTask(
            taskId = taskId,
            url = url,
            fileName = fileName,
            fileSize = fileSize,
            savePath = savePath,
            chunkCount = chunkCount
        )
    }

    companion object {
        /** 启动下载的 Action。 */
        const val ACTION_START_DOWNLOAD = "com.jieyundu.app.action.START_DOWNLOAD"

        /** 初始进度百分比。 */
        private const val INITIAL_PERCENT = 0

        /**
         * 实时进度静默超时：60 秒。
         *
         * 说明：引擎在任务运行期间至少每 200ms 刷新一次实时快照；若 60 秒都没有该任务的更新，
         * 说明引擎侧已不再持有它（例如进程被回收后重建），此时结束该任务的前台观察，避免通知常驻。
         */
        private const val LIVE_PROGRESS_TIMEOUT_MILLIS = 60_000L

        /** Intent extra 键。 */
        private const val EXTRA_TASK_ID = "extra_task_id"
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_FILE_NAME = "extra_file_name"
        private const val EXTRA_FILE_SIZE = "extra_file_size"
        private const val EXTRA_SAVE_PATH = "extra_save_path"
        private const val EXTRA_CHUNK_COUNT = "extra_chunk_count"

        /**
         * 构造启动下载服务的 Intent。
         *
         * @param context 上下文。
         * @param task 运行态任务。
         * @return 可直接交给 `startForegroundService` 的 Intent。
         */
        fun buildStartIntent(context: Context, task: DownloadTask): Intent =
            Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_TASK_ID, task.taskId)
                putExtra(EXTRA_URL, task.url)
                putExtra(EXTRA_FILE_NAME, task.fileName)
                putExtra(EXTRA_FILE_SIZE, task.fileSize)
                putExtra(EXTRA_SAVE_PATH, task.savePath)
                putExtra(EXTRA_CHUNK_COUNT, task.chunkCount)
            }

        /**
         * 启动前台下载服务（C2 接线入口）。
         *
         * 说明：由下载入口在投递任务时调用。Android 12+ 若在后台调用可能抛
         * `ForegroundServiceStartNotAllowedException`，此处捕获并记日志——下载本身由引擎执行，
         * 服务仅承担保活与通知，启动失败不应影响下载。
         *
         * @param context 上下文。
         * @param task 运行态任务。
         */
        fun start(context: Context, task: DownloadTask) {
            try {
                ContextCompat.startForegroundService(context, buildStartIntent(context, task))
            } catch (exception: RuntimeException) {
                Timber.w(exception, "DownloadService start rejected for task %s", task.taskId)
            }
        }
    }
}
