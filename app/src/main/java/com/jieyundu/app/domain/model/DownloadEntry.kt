// 文件：DownloadEntry.kt
// 职责：下载任务的持久化模型（写入数据库/恢复任务时使用）
// 依赖：NetdiskType
// 协议：AGPL-3.0

package com.jieyundu.app.domain.model

/**
 * 下载任务的持久化记录。
 *
 * 与运行态模型的边界：
 * - 本类只描述“要下什么、下到哪、下了多少”，可被序列化落库；
 * - 运行态任务（含实时状态、协程句柄）见阶段 4 的 `domain/downloader/DownloadTask.kt`；
 * - 两者之间的转换由 `data/repository/DownloadRepository.kt`（阶段 5）负责。
 *
 * @property taskId 任务唯一 ID。
 * @property netdiskType 来源网盘类型。
 * @property fileName 落盘文件名。
 * @property fileSize 文件总大小，单位字节；未知时为 -1。
 * @property downloadUrl 下载直链（有时效，过期后需重新解析）。
 * @property savePath 目标文件绝对路径。
 * @property chunkCount 分片数量（并发数），默认 8，上限 32。
 * @property downloadedBytes 已下载字节数（断点续传依据）。
 * @property createdAt 创建时间戳（毫秒）。
 * @property updatedAt 最近更新时间戳（毫秒）。
 */
data class DownloadEntry(
    val taskId: String,
    val netdiskType: NetdiskType,
    val fileName: String,
    val fileSize: Long,
    val downloadUrl: String,
    val savePath: String,
    val chunkCount: Int,
    val downloadedBytes: Long,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** 下载进度，范围 0f..1f；大小未知时返回 0f。 */
    val progress: Float
        get() = if (fileSize <= 0L) {
            0f
        } else {
            (downloadedBytes.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f)
        }

    /** 是否已下载完成。 */
    val isFinished: Boolean
        get() = fileSize > 0L && downloadedBytes >= fileSize
}
