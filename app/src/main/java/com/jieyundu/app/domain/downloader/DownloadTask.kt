// 文件：DownloadTask.kt
// 职责：描述一个运行态下载任务所需的全部输入参数
// 依赖：无
// 协议：AGPL-3.0

package com.jieyundu.app.domain.downloader

/**
 * 运行态下载任务。
 *
 * 与持久化模型的边界：
 * - 本类只承载「运行一次下载」需要的参数，不含状态与协程句柄；
 * - 落库模型见 `domain/model/DownloadEntry.kt`（阶段 2）；
 * - Room 实体映射由 `data/repository/DownloadRepository.kt`（阶段 5）负责。
 *
 * @property taskId 任务唯一 ID。
 * @property url 下载直链（有时效，过期后需由上层重新解析）。
 * @property fileName 落盘文件名。
 * @property fileSize 文件总大小，单位字节；未知时为 -1，此时退化为单分片开放式下载。
 * @property savePath 目标文件绝对路径。
 * @property chunkCount 分片数量，等价于并发数；取值会被收敛到 1..32。
 * @property headers 额外请求头（例如网盘要求的 Cookie / Referer）。
 */
data class DownloadTask(
    val taskId: String,
    val url: String,
    val fileName: String,
    val fileSize: Long,
    val savePath: String,
    val chunkCount: Int = DEFAULT_CHUNK_COUNT,
    val headers: Map<String, String> = emptyMap()
) {
    /** 文件大小是否已知。 */
    val hasKnownSize: Boolean
        get() = fileSize > 0L

    companion object {
        /** 默认并发数。 */
        const val DEFAULT_CHUNK_COUNT = 8

        /** 文件大小未知时的默认值。 */
        const val DEFAULT_FILE_SIZE = -1L

        /** 并发数下限。 */
        const val MIN_CHUNK_COUNT = 1

        /** 并发数上限（《要求.md》4.2）。 */
        const val MAX_CHUNK_COUNT = 32
    }
}
