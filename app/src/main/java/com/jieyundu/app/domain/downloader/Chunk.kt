// 文件：Chunk.kt
// 职责：描述下载文件的一个分片及其续传进度
// 依赖：无
// 协议：AGPL-3.0

package com.jieyundu.app.domain.downloader

/**
 * 文件分片描述。
 *
 * 区间语义：HTTP Range 使用的是**闭区间**，即 `[start, end]` 两端都包含。
 *
 * 未知大小：`end == [UNKNOWN_END]` 表示文件总大小未知，此时按开放式 Range
 * （`bytes=start-`）请求，直到服务端返回 EOF。
 *
 * @property index 分片序号，从 0 开始，同时用作临时分片文件名后缀。
 * @property start 起始字节偏移（包含）。
 * @property end 结束字节偏移（包含）；未知大小时为 [UNKNOWN_END]。
 * @property downloadedBytes 本分片已落盘的字节数（断点续传依据）。
 */
data class Chunk(
    val index: Int,
    val start: Long,
    val end: Long,
    val downloadedBytes: Long = 0L
) {
    /** 本分片总字节数；未知大小时返回 -1。 */
    val totalBytes: Long
        get() = if (isUnknownSize()) UNKNOWN_SIZE else (end - start + 1L)

    /** 本分片是否已完成。 */
    val isCompleted: Boolean
        get() = if (isUnknownSize()) false else downloadedBytes >= totalBytes

    /** 是否为“大小未知”的开放式分片。 */
    fun isUnknownSize(): Boolean = end == UNKNOWN_END

    /** 返回一个已更新续传进度的新分片。 */
    fun withDownloaded(bytes: Long): Chunk = copy(downloadedBytes = bytes)

    companion object {
        /** 未知结束位置的哨兵值。 */
        const val UNKNOWN_END = Long.MAX_VALUE

        /** 未知大小的哨兵值。 */
        const val UNKNOWN_SIZE = -1L
    }
}
