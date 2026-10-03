// 文件：DownloadService.kt
// 职责：下载前台服务，托管下载引擎并把进度同步到通知栏
// 依赖：DownloadEngine、NotificationHelper、DownloadTask、DownloadState
// 协议：AGPL-3.0

package com.jieyundu.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.jieyundu.app.domain.downloader.DownloadEngine
import com.jieyundu.app.domain.downloader.DownloadState
import com.jieyundu.app.domain.downloader.DownloadTask
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 下载前台服务。
 *
 * 职责：
 * 1. 以前台服务身份启动，保证后台下载不被系统回收；
 * 2. 调用 [DownloadEngine.start] 执行下载；
 * 3. 把进度同步到通知栏，完成后自动撤下前台。
 *
 * 说明：服务本身不持有业务状态，任务信息通过 Intent extra 传入。
 */
@AndroidEntryPoint
class DownloadService : Service() {

    /** 下载引擎（由 Hilt 注入）。 */
    @Inject
    lateinit var engine: DownloadEngine

    /** 通知构建工具（由 Hilt 注入）。 */
    @Inject
    lateinit var notificationHelper: NotificationHelper

    /** 服务自有作用域，销毁时统一取消。 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 当前正在观察的任务协程。 */
    private var observeJob: Job? = null

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
        startForegroundWithNotification(task.fileName, 0)
        observeJob?.cancel()
        observeJob = serviceScope.launch { runDownload(task) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * 启动下载并持续把进度写入通知。
     *
     * @param task 运行态任务。
     */
    private suspend fun runDownload(task: DownloadTask) {
        try {
            engine.start(task)
            engine.observeProgress(task.taskId).collectLatest { progress ->
                when (progress.state) {
                    DownloadState.COMPLETED -> {
                        notificationHelper.notify(
                            NotificationHelper.NOTIFICATION_ID_PROGRESS,
                            notificationHelper.buildCompletedNotification(task.fileName)
                        )
                        stopForegroundCompat()
                        stopSelf()
                    }

                    DownloadState.DOWNLOADING -> {
                        notificationHelper.notify(
                            NotificationHelper.NOTIFICATION_ID_PROGRESS,
                            notificationHelper.buildProgressNotification(task.fileName, progress.percent)
                        )
                    }

                    DownloadState.PAUSED,
                    DownloadState.FAILED,
                    DownloadState.CANCELED -> {
                        notificationHelper.cancel(NotificationHelper.NOTIFICATION_ID_PROGRESS)
                        stopForegroundCompat()
                        stopSelf()
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
        }
    }

    /**
     * 以 dataSync 类型进入前台并挂出初始通知。
     *
     * @param fileName 文件名。
     * @param percent 初始进度。
     */
    private fun startForegroundWithNotification(fileName: String, percent: Int) {
        val notification = notificationHelper.buildProgressNotification(fileName, percent)
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID_PROGRESS,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    /** 撤下前台状态但保留通知。 */
    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
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
    }
}
