// 文件：DownloadTaskRuntime.kt
// 职责：单个下载任务的运行态数据（状态流 / 进度流 / 分片 / 测速采样点）
// 依赖：Job、MutableStateFlow、AtomicLong、DownloadTask、Chunk
// 协议：AGPL-3.0

package com.jieyundu.app.domain.downloader

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

// 【修订 JYD-DEBT3-2026-10-07】原为 DownloadEngine.kt 内的 private class TaskRuntime，
// 拆文件后改名并放宽为同包 internal（仅该包的引擎与分片下载器使用，不对外暴露）。

/**
 * 单个任务的运行态数据。
 *
 * 说明：本类是**纯数据容器**（状态流 / 进度流 / 分片 / 测速采样点），不含任何下载逻辑；
 * 调度与进度计算留在 [DownloadEngine]，分片请求在 [ChunkDownloader]。
 *
 * @param task 任务描述。
 * @param chunkCount 分片数，用于构造初始进度快照。
 */
internal class DownloadTaskRuntime(
    val task: DownloadTask,
    chunkCount: Int
) {

    /** 离散状态。 */
    val state: MutableStateFlow<DownloadState> = MutableStateFlow(DownloadState.PENDING)

    /** 进度快照。 */
    val progress: MutableStateFlow<DownloadProgressState> =
        MutableStateFlow(DownloadProgressState.initial(task.taskId, chunkCount))

    /** 当前任务协程句柄。 */
    var job: Job? = null

    /** 当前分片列表。 */
    @Volatile
    var chunks: List<Chunk> = emptyList()

    /** 本次运行累计写入字节数（原子，多个分片协程并发写入）。 */
    val sessionBytes: AtomicLong = AtomicLong(0L)

    /** 本次运行开始前已落盘的字节数（续传起点）。 */
    @Volatile
    var initialBytes: Long = 0L

    /** 上一次进度发射时间戳，用于节流。 */
    @Volatile
    var lastEmitAt: Long = 0L

    /** 上一次测速采样时间戳。 */
    @Volatile
    var lastSpeedSampleAt: Long = 0L

    /** 上一次测速采样时的字节数。 */
    @Volatile
    var lastSpeedSampleBytes: Long = 0L

    /** 本次运行开始时间戳（用于计算平均速度）。 */
    @Volatile
    var sessionStartAt: Long = 0L
}
