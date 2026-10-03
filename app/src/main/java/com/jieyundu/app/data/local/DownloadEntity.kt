// 文件：DownloadEntity.kt
// 职责：下载进度的 Room 持久化实体
// 依赖：DownloadProgressState、DownloadState、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jieyundu.app.domain.downloader.DownloadProgressState
import com.jieyundu.app.domain.downloader.DownloadState
import timber.log.Timber

/**
 * 下载进度持久化实体，主键为任务 ID。
 *
 * 说明：本实体承载下载进度端口（`DownloadProgressPort`）能提供的信息，
 * 并自【修订 JYD-SAVEPATH-2026-10-03】起额外持久化落盘路径 [savePath]；
 * 文件名等纯展示字段仍属历史记录，见 [HistoryEntity]。
 *
 * @property taskId 任务 ID。
 * @property downloadedBytes 已下载字节数。
 * @property totalBytes 总字节数；未知时为 -1。
 * @property state 离散状态枚举名。
 * @property chunkCount 分片总数。
 * @property completedChunks 已完成分片数。
 * @property updatedAt 最后更新时间戳。
 * @property savePath 目标文件绝对路径；未知时为 null。
 *   【修订 JYD-SAVEPATH-2026-10-03】新增 `save_path` 列，用于持久化落盘路径。
 */
@Entity(tableName = "download_progress")
data class DownloadEntity(
    @PrimaryKey
    @ColumnInfo(name = "task_id")
    val taskId: String,
    @ColumnInfo(name = "downloaded_bytes")
    val downloadedBytes: Long,
    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long,
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "chunk_count")
    val chunkCount: Int,
    @ColumnInfo(name = "completed_chunks")
    val completedChunks: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "save_path")
    val savePath: String? = null
) {

    /**
     * 转换为领域层进度快照。
     *
     * @return 进度快照；速度字段因持久化不保存该值而以
     * [DownloadProgressState.UNKNOWN_SIZE] 占位。
     */
    fun toProgress(): DownloadProgressState = DownloadProgressState(
        taskId = taskId,
        state = parseState(state),
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        speedBytesPerSecond = DownloadProgressState.UNKNOWN_SIZE,
        chunkCount = chunkCount,
        completedChunks = completedChunks,
        savePath = savePath
    )

    companion object {
        /**
         * 由进度快照构造实体。
         *
         * @param progress 进度快照。
         * @param updatedAt 写入时间戳，默认取当前时间。
         * @return 实体。
         */
        fun fromProgress(
            progress: DownloadProgressState,
            updatedAt: Long = System.currentTimeMillis()
        ): DownloadEntity = DownloadEntity(
            taskId = progress.taskId,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes,
            state = progress.state.name,
            chunkCount = progress.chunkCount,
            completedChunks = progress.completedChunks,
            updatedAt = updatedAt,
            savePath = progress.savePath
        )

        /**
         * 安全解析枚举字符串，遇到脏数据时回退为 [DownloadState.PENDING] 并记录日志（D15）。
         *
         * @param raw 持久化的枚举名。
         * @return 解析结果或回退值。
         */
        private fun parseState(raw: String): DownloadState = try {
            DownloadState.valueOf(raw)
        } catch (exception: IllegalArgumentException) {
            Timber.e(exception, "Unknown persisted download state: %s", raw)
            DownloadState.PENDING
        }
    }
}