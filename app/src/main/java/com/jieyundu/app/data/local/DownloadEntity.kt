// 文件：DownloadEntity.kt
// 职责：下载进度的 Room 持久化实体（含任务名与续传所需的直链 / 请求头 / 转存关联）
// 依赖：DownloadProgressState、DownloadState、DownloadTaskRecord、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jieyundu.app.domain.downloader.DownloadProgressState
import com.jieyundu.app.domain.downloader.DownloadState
import com.jieyundu.app.domain.downloader.DownloadTaskRecord
import timber.log.Timber

/**
 * 下载进度持久化实体，主键为任务 ID。
 *
 * 说明：本实体承载下载进度端口（`DownloadProgressPort`）能提供的信息，并自
 * 【JYD-SAVEPATH-2026-10-03】起持久化落盘路径；自【JYD-P1-2026-10-04】起额外持久化
 * **任务名**（P1-1：修复重启后显示「未命名」）、**直链与请求头**（P1-2：重启后据其续传）
 * （P1-3 的转存副本关联由独立的 `transfer_records` 表承担，不在本表冗余存列）。
 *
 * @property taskId 任务 ID。
 * @property downloadedBytes 已下载字节数。
 * @property totalBytes 总字节数；未知时为 -1。
 * @property state 离散状态枚举名。
 * @property chunkCount 分片总数。
 * @property completedChunks 已完成分片数。
 * @property updatedAt 最后更新时间戳。
 * @property savePath 目标文件绝对路径；未知时为 null。
 * @property fileName 任务名（文件名）；老数据迁移后为空串，UI 回退「未命名任务」。
 * @property url 下载直链（P1-2）；老数据为 null，重启续传时提示重新下载。
 * @property headers 请求头（每行 `name: value` 的持久化字符串，P1-2）；老数据为 null。
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
    val savePath: String? = null,
    @ColumnInfo(name = "file_name", defaultValue = "")
    val fileName: String = "",
    @ColumnInfo(name = "url")
    val url: String? = null,
    @ColumnInfo(name = "headers")
    val headers: String? = null
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

    /**
     * 转换为领域层「任务存档」（P1-2：重启后据此重建可续传的任务）。
     *
     * @return 任务存档；缺少直链的旧记录返回 null（调用方回退到「请重新下载」提示）。
     */
    fun toTaskRecord(): DownloadTaskRecord? {
        val persistedUrl = url?.takeIf { value -> value.isNotBlank() } ?: return null
        val persistedPath = savePath?.takeIf { value -> value.isNotBlank() } ?: return null
        return DownloadTaskRecord(
            taskId = taskId,
            url = persistedUrl,
            fileName = fileName,
            fileSize = totalBytes,
            savePath = persistedPath,
            chunkCount = chunkCount,
            headers = decodeHeaders(headers)
        )
    }

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
            savePath = progress.savePath,
            // 进度快照不承载任务名 / 直链（避免污染端口契约），这四列由专用写入方法维护，
            // 详见 [DownloadDao.upsertTask]：REPLACE 覆盖时若此处留空会把已有值清掉。
            fileName = "",
            url = null,
            headers = null
        )

        /**
         * 由任务存档构造实体（P1-2）。
         *
         * @param record 任务存档。
         * @param state 初始状态。
         * @param downloadedBytes 已下载字节数。
         * @param updatedAt 写入时间戳。
         * @return 实体。
         */
        fun fromTaskRecord(
            record: DownloadTaskRecord,
            state: String,
            downloadedBytes: Long,
            updatedAt: Long = System.currentTimeMillis()
        ): DownloadEntity = DownloadEntity(
            taskId = record.taskId,
            downloadedBytes = downloadedBytes,
            totalBytes = record.fileSize,
            state = state,
            chunkCount = record.chunkCount,
            completedChunks = 0,
            updatedAt = updatedAt,
            savePath = record.savePath,
            fileName = record.fileName,
            url = record.url,
            headers = encodeHeaders(record.headers)
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

        /**
         * 把请求头编码为持久化字符串（每行 `name: value`）。
         *
         * 说明：刻意不引入 JSON 依赖——请求头是简单的 `Map<String, String>`，
         * 逐行序列化既直观又便于人工排查；解码失败时按行丢弃而不是整段崩掉。
         *
         * @param headers 请求头。
         * @return 持久化字符串；空表返回 null。
         */
        internal fun encodeHeaders(headers: Map<String, String>): String? {
            val meaningful = headers.filter { (name, value) ->
                name.isNotBlank() && value.isNotBlank()
            }
            if (meaningful.isEmpty()) {
                return null
            }
            return meaningful.entries.joinToString(separator = "\n") { (name, value) ->
                "$name: $value"
            }
        }

        /**
         * 解码持久化的请求头字符串。
         *
         * @param raw 持久化字符串。
         * @return 请求头表；无内容时返回空表。
         */
        internal fun decodeHeaders(raw: String?): Map<String, String> {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) {
                return emptyMap()
            }
            return text.lineSequence()
                .mapNotNull { line ->
                    val separator = line.indexOf(HEADER_SEPARATOR)
                    if (separator <= 0) {
                        null
                    } else {
                        val name = line.substring(0, separator).trim()
                        val value = line.substring(separator + HEADER_SEPARATOR.length).trim()
                        if (name.isEmpty() || value.isEmpty()) null else name to value
                    }
                }
                .toMap()
        }

        /** 请求头持久化分隔符。 */
        private const val HEADER_SEPARATOR = ": "
    }
}
