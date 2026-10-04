// 文件：DownloadService.kt
// 职责：下载前台服务，托管下载引擎、持有唤醒锁并把进度同步到通知栏（C2）
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
 * 职责：
 * 1. 以前台服务身份启动，保证后台下载不被系统随意回收；
 * 2. 调用 [DownloadEngine.start] 执行下载；
 * 3. 按设置（[AppSettingsStore.downloadNotificationEnabled]）决定是否把进度同步到通知栏；
 * 4. 按设置（[AppSettingsStore.keepDownloadingOnLock]）在下载期间持有 `PARTIAL_WAKE_LOCK`；
 * 5. 任务到达终态后撤下前台、停止自身并释放唤醒锁。
 *
 * 说明：服务本身不持有业务状态，任务信息通过 Intent extra 传入；进度来源为引擎的实时快照
 * （[DownloadEngine.liveProgress]），与下载页同源、且不额外触碰数据库。
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

    /** 当前正在观察的任务协程。 */
    private var observeJob: Job? = null

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
        if (!startForegroundWithNotification(task.fileName, INITIAL_PERCENT)) {
            // 被系统拒绝时不自尽：下载已由引擎执行，服务仅承担保活与通知。
            Timber.w("DownloadService startForeground rejected; keep running without foreground")
        }
        acquireWakeLockIfNeeded()
        observeJob?.cancel()
        observeJob = serviceScope.launch { runDownload(task) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    /**
     * 启动下载并持续把进度写入通知，直到任务到达终态。
     *
     * @param task 运行态任务。
     */
    private suspend fun runDownload(task: DownloadTask) {
        try {
            engine.start(task)
            // 实时快照在下载中不断刷新、终态也会发布；60 秒无更新视为引擎已不再持有该任务。
            engine.liveProgress
                .map { live -> live[task.taskId] }
                .filterNotNull()
                .timeout(LIVE_PROGRESS_TIMEOUT_MILLIS.milliseconds)
                .collect { progress ->
                    when (progress.state) {
                        DownloadState.COMPLETED -> {
                            notificationHelper.notify(
                                NotificationHelper.NOTIFICATION_ID_PROGRESS,
                                notificationHelper.buildCompletedNotification(task.fileName),
                                settings.downloadNotificationEnabled.value
                            )
                            finishForeground()
                        }

                        DownloadState.DOWNLOADING -> {
                            notificationHelper.notify(
                                NotificationHelper.NOTIFICATION_ID_PROGRESS,
                                notificationHelper.buildProgressNotification(
                                    task.fileName,
                                    progress.percent
                                ),
                                settings.downloadNotificationEnabled.value
                            )
                        }

                        DownloadState.PAUSED,
                        DownloadState.FAILED,
                        DownloadState.CANCELED -> {
                            notificationHelper.cancel(NotificationHelper.NOTIFICATION_ID_PROGRESS)
                            finishForeground()
                        }

                        DownloadState.PENDING -> Unit
                    }
                }
        } catch (cancellation: CancellationException) {
            // C3：服务销毁导致的取消，原样抛出
            throw cancellation
        } catch (exception: Exception) {
            Timber.e(exception, "DownloadService runDownload failed for task %s", task.taskId)
            notificationHelper.cancel(NotificationHelper.NOTIFICATION_ID_PROGRESS)
            stopSelf()
        } finally {
            releaseWakeLock()
        }
    }

    /**
     * 收尾：撤下前台状态、清掉进度通知并停止服务。
     */
    private fun finishForeground() {
        stopForegroundCompat()
        stopSelf()
        releaseWakeLock()
    }

    /**
     * 以 dataSync 类型进入前台并挂出初始通知（C2 第 2 条：按设置决定通知详略）。
     *
     * @param fileName 文件名。
     * @param percent 初始进度。
     * @return true 表示成功进入前台；false 表示被系统拒绝（含后台启动限制）。
     */
    private fun startForegroundWithNotification(fileName: String, percent: Int): Boolean {
        val notification = if (settings.downloadNotificationEnabled.value) {
            notificationHelper.buildProgressNotification(fileName, percent)
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
         * 说明引擎侧已不再持有它（例如进程被回收后重建），此时结束前台观察，避免通知栏常驻。
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
         * 说明：由首页在投递下载任务时调用。Android 12+ 若在后台调用可能抛
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
