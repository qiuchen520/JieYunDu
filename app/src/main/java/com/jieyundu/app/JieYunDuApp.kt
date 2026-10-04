// 文件：JieYunDuApp.kt
// 职责：Application 入口——初始化 Hilt / Timber / 崩溃捕获，并做启动期状态协调（P1）
// 依赖：Timber、Hilt、CrashReporter、RecentLogTree、DownloadDao、TransferRecordDao、TempFolderGuard
// 协议：AGPL-3.0

package com.jieyundu.app

import android.app.Application
import com.jieyundu.app.data.local.DownloadDao
import com.jieyundu.app.data.local.TransferRecordDao
import com.jieyundu.app.domain.downloader.DownloadState
import com.jieyundu.app.domain.transfer.TempFolderGuard
import com.jieyundu.app.util.CrashReporter
import com.jieyundu.app.util.RecentLogTree
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 极云渡 Application 入口。
 *
 * 职责：
 * 1. 通过 [HiltAndroidApp] 生成依赖注入容器；
 * 2. 注册全局崩溃捕获（[CrashReporter.install]），让闪退可被记录与导出；
 * 3. 挂载 [RecentLogTree] 收集崩溃前最近的 Timber 日志；
 * 4. Debug 构建下额外挂载 [Timber.DebugTree]（C8：日志统一走 Timber）；
 * 5. 【JYD-P1-2026-10-04】启动期状态协调（[reconcileOnStartup]）：
 *    - 把库里残留的「等待中 / 下载中」任务纠正为「已暂停」（P1-2，
 *      否则界面显示下载中却永远不动，与「点了没反应」观感一致）；
 *    - 从 `transfer_records` 水合删除守卫的副本集合（P1-3，
 *      否则重启后无法识别重启前转存产生的临时副本）。
 */
@HiltAndroidApp
class JieYunDuApp : Application() {

    /** 下载进度 DAO（用于启动期状态纠正）。 */
    @Inject
    lateinit var downloadDao: DownloadDao

    /** 转存登记 DAO（用于启动期恢复删除守卫的放行集合）。 */
    @Inject
    lateinit var transferRecordDao: TransferRecordDao

    /** 启动期协调用的自有作用域（不阻塞主线程）。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 崩溃捕获需尽早注册：先装日志树，再注册异常处理器，保证崩溃上下文尽量完整。
        Timber.plant(RecentLogTree())
        CrashReporter.install(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        reconcileOnStartup()
    }

    /**
     * 启动期状态协调（P1-2 状态纠正 + P1-3 守卫水合）。
     *
     * 说明：全部在 IO 作用域内执行，失败只记日志、绝不阻断启动（D15）。
     */
    private fun reconcileOnStartup() {
        appScope.launch {
            markInterruptedDownloadsPaused()
        }
        appScope.launch {
            hydrateTransferGuard()
        }
    }

    /**
     * 把库里处于活动态（等待中 / 下载中）的任务纠正为「已暂停」（P1-2）。
     *
     * 背景：进程被杀后内存运行态消失，但库里仍留着 `PENDING` / `DOWNLOADING`。
     */
    private suspend fun markInterruptedDownloadsPaused() {
        try {
            val corrected = downloadDao.markActiveAsPaused(
                fromStates = listOf(
                    DownloadState.PENDING.name,
                    DownloadState.DOWNLOADING.name
                ),
                toState = DownloadState.PAUSED.name,
                updatedAt = System.currentTimeMillis()
            )
            if (corrected > 0) {
                Timber.i("JieYunDuApp marked %d interrupted download(s) as PAUSED", corrected)
            }
        } catch (exception: Exception) {
            Timber.e(exception, "JieYunDuApp failed to reconcile interrupted downloads")
        }
    }

    /**
     * 从持久化登记恢复删除守卫的「转存副本」集合（P1-3）。
     *
     * 说明：水合后，「手动清理」在重启后依然能识别并清理重启前产生的临时副本；
     * 未登记的 fid 仍按守卫的默认拒绝语义处理（宁可不删，也不误删用户文件）。
     */
    private suspend fun hydrateTransferGuard() {
        try {
            val records = transferRecordDao.queryAll()
            if (records.isEmpty()) {
                return
            }
            TempFolderGuard.registerAll(records.map { record -> record.fid })
            records.mapNotNull { record -> record.dirFid }
                .filter { dirFid -> dirFid.isNotBlank() }
                .forEach { dirFid -> TempFolderGuard.register(dirFid) }
            Timber.i("JieYunDuApp hydrated %d transfer record(s) into TempFolderGuard", records.size)
        } catch (exception: Exception) {
            Timber.e(exception, "JieYunDuApp failed to hydrate TempFolderGuard")
        }
    }
}
