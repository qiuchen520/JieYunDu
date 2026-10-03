// 文件：DownloadState.kt
// 职责：定义下载任务的离散状态枚举与实时进度快照
// 依赖：无
// 协议：AGPL-3.0

package com.jieyundu.app.domain.downloader

/**
 * 下载任务状态枚举。
 *
 * 状态迁移（正常路径）：
 * `PENDING → DOWNLOADING → COMPLETED`
 * 旁路：`PAUSED`（可回 `DOWNLOADING`）、`FAILED`、`CANCELED`（终态）。
 */
enum class DownloadState {
    /** 尚未开始。 */
    PENDING,

    /** 正在下载。 */
    DOWNLOADING,

    /** 已暂停，可续传。 */
    PAUSED,

    /** 下载完成。 */
    COMPLETED,

    /** 失败，可重试。 */
    FAILED,

    /** 已取消（分片临时文件已清理）。 */
    CANCELED
}

/**
 * 实时进度快照。
 *
 * 存在的理由：《要求.md》4.2 要求界面实时显示速度与进度，而 [DownloadState] 只是离散状态，
 * 无法承载数值；因此引擎额外提供 `observeProgress()` 返回本类型。
 *
 * 字段要求（【修订 JYD-ERRATA-2026-10-03】修订二）：本类型至少须包含
 * `taskId / downloadedBytes / totalBytes / speedBytesPerSecond / percent / state`，
 * 以满足下载进度落库端口（`DownloadProgressPort.upsert`）的需要。
 *
 * @property taskId 任务 ID，同时作为持久化主键。
 * @property state 当前离散状态。
 * @property downloadedBytes 已下载字节数。
 * @property totalBytes 总字节数；未知时为 -1。
 * @property speedBytesPerSecond 瞬时速度；未知时为 -1。
 * @property chunkCount 分片总数。
 * @property completedChunks 已完成分片数。
 */
data class DownloadProgressState(
    val taskId: String,
    val state: DownloadState,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
    val chunkCount: Int,
    val completedChunks: Int
) {
    /** 进度百分比，范围 0..100；大小未知时返回 0。 */
    val percent: Int
        get() = if (totalBytes <= 0L) {
            0
        } else {
            ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        }

    companion object {
        /** 大小未知的占位值。 */
        const val UNKNOWN_SIZE = -1L

        /** 构造一个初始快照。 */
        fun initial(taskId: String, chunkCount: Int): DownloadProgressState = DownloadProgressState(
            taskId = taskId,
            state = DownloadState.PENDING,
            downloadedBytes = 0L,
            totalBytes = UNKNOWN_SIZE,
            speedBytesPerSecond = UNKNOWN_SIZE,
            chunkCount = chunkCount,
            completedChunks = 0
        )
    }
}
