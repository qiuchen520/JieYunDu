// 文件：DownloadProgressTracker.kt
// 职责：下载进度的内存实时表与测速——节流刷新、瞬时/平均速度计算、发布只读流
// 依赖：MutableStateFlow、DownloadProgressState、DownloadTaskRuntime、DownloadEngine.ACTIVE_STATES
// 协议：AGPL-3.0

package com.jieyundu.app.domain.downloader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 下载进度与测速（【修订 JYD-DEBT10-2026-10-07】自 [DownloadEngine] 拆出）。
 *
 * 存在理由：实时进度（Owner 反馈「下载页速度恒 `--`」的修复）本是**独立关注点**——
 * 「节流刷新 + 测速换算 + 内存实时表」与「任务调度 / 分片下载」没有耦合；混在引擎里时
 * 引擎同时持有下载逻辑与展示口径。拆出后引擎只管调度与生命周期，本类只管进度口径。
 *
 * 约定：
 * - 内存态、**不落库**（Owner 要求③：不为显示速度频繁写 Room）；落库仍只发生在引擎的
 *   start / pause / 完成 / 失败四刻；
 * - 分片协程会并发调用 [onBytesRead]，故实时表用「读当前值 + 生成新 Map + CAS 回写」；
 * - 节流与「仅活动态刷新」都在本类内完成，调用方无需关心。
 */
internal class DownloadProgressTracker {

    /** 全部任务的实时进度快照（任务 ID → 进度）。 */
    private val liveFlow: MutableStateFlow<Map<String, DownloadProgressState>> =
        MutableStateFlow(emptyMap())

    /** 实时进度快照的只读流（供 UI 订阅）。 */
    val live: StateFlow<Map<String, DownloadProgressState>> = liveFlow.asStateFlow()

    /**
     * 把一份进度快照写入内存实时表。
     *
     * @param progress 最新进度快照（含任务 ID）。
     */
    fun publish(progress: DownloadProgressState) {
        liveFlow.update { current -> current + (progress.taskId to progress) }
    }

    /**
     * 从内存实时表中移除某任务的条目（任务取消后调用）。
     *
     * @param taskId 任务 ID。
     */
    fun remove(taskId: String) {
        liveFlow.update { current -> current - taskId }
    }

    /**
     * 分片读到一批字节后刷新进度与测速（内部节流）。
     *
     * 说明：
     * - 仅**活动态**才刷新：pause() 取消协程到真正停下的短暂窗口内，在途分片可能仍调用本方法；
     *   若此时发布 DOWNLOADING 快照，会把刚写入的「已暂停」覆盖回去（JYD-DLSPEED2-2026-10-04）；
     * - 瞬时速度 = (本次累计已下载 − 上次采样) ÷ 采样间隔；
     *   平均速度 = 本次运行累计字节 ÷ 本次运行已进行时长；
     * - 刷新后写入 [DownloadTaskRuntime.progress] 并同步到实时表（不落库）。
     *
     * @param runtime 运行态任务。
     * @param sessionBytes 本次运行累计写入的字节数。
     */
    fun onBytesRead(runtime: DownloadTaskRuntime, sessionBytes: Long) {
        if (runtime.state.value !in DownloadEngine.ACTIVE_STATES) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - runtime.lastEmitAt < PROGRESS_INTERVAL_MILLIS) {
            return
        }
        runtime.lastEmitAt = now

        val downloaded = runtime.initialBytes + sessionBytes
        val elapsed = now - runtime.lastSpeedSampleAt
        val speed = if (elapsed > 0L) {
            (downloaded - runtime.lastSpeedSampleBytes) * 1000L / elapsed
        } else {
            DownloadProgressState.UNKNOWN_SIZE
        }
        runtime.lastSpeedSampleAt = now
        runtime.lastSpeedSampleBytes = downloaded

        // 平均速度（Owner 反馈）：本运行累计写入字节 ÷ 本运行已进行的时长，反映整体吞吐。
        val sessionElapsed = now - runtime.sessionStartAt
        val averageSpeed = if (sessionElapsed > 0L) {
            sessionBytes * 1000L / sessionElapsed
        } else {
            DownloadProgressState.UNKNOWN_SIZE
        }

        runtime.progress.value = runtime.progress.value.copy(
            state = runtime.state.value,
            downloadedBytes = downloaded,
            totalBytes = if (runtime.task.hasKnownSize) {
                runtime.task.fileSize
            } else {
                DownloadProgressState.UNKNOWN_SIZE
            },
            speedBytesPerSecond = speed,
            averageSpeedBytesPerSecond = averageSpeed
        )
        publish(runtime.progress.value)
    }

    private companion object {
        /** 进度发射节流间隔。 */
        const val PROGRESS_INTERVAL_MILLIS = 200L
    }
}
