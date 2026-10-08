// 文件：ChunkManager.kt
// 职责：负责文件分片切分、临时分片文件的命名与合并
// 依赖：Chunk、DownloadTask
// 协议：AGPL-3.0

package com.jieyundu.app.domain.downloader

import java.io.File
import java.io.IOException
import timber.log.Timber

/**
 * 分片管理器。
 *
 * 职责边界：
 * - **只做纯计算与文件合并**，不发起网络请求、不持有协程；
 * - 因此可在 JVM 单元测试中直接验证切分数学与合并结果（见 ChunkManagerTest）。
 *
 * @param defaultChunkCount 默认分片数（并发数）。
 */
class ChunkManager(
    private val defaultChunkCount: Int = DownloadTask.DEFAULT_CHUNK_COUNT
) {

    /**
     * 按文件大小切分分片。
     *
     * 规则：
     * 1. 大小未知（<= 0）→ 返回单个开放式分片，保证仍能下载；
     * 2. 分片数被收敛到 `32..512`（B2 功能②），且不超过文件字节数（小文件不会被切成 512 片）；
     * 3. 余数从第 0 片起逐片 +1，保证区间连续、无空洞、无重叠、完整覆盖 `[0, fileSize-1]`。
     *
     * @param fileSize 文件总大小，单位字节。
     * @param chunkCount 期望分片数；不传则用构造参数。
     * @return 分片列表，序号从 0 递增。
     */
    fun createChunks(
        fileSize: Long,
        chunkCount: Int = defaultChunkCount
    ): List<Chunk> {
        if (fileSize <= 0L) {
            return listOf(
                Chunk(
                    index = 0,
                    start = 0L,
                    end = Chunk.UNKNOWN_END,
                    downloadedBytes = 0L
                )
            )
        }

        val effectiveCount = normalizeChunkCount(chunkCount, fileSize)
        val baseSize = fileSize / effectiveCount
        val remainder = (fileSize % effectiveCount).toInt()

        val chunks = ArrayList<Chunk>(effectiveCount)
        var cursor = 0L
        for (index in 0 until effectiveCount) {
            val size = baseSize + if (index < remainder) 1L else 0L
            val start = cursor
            val end = cursor + size - 1L
            chunks += Chunk(index = index, start = start, end = end, downloadedBytes = 0L)
            cursor = end + 1L
        }
        return chunks
    }

    /**
     * 计算某个分片对应的临时文件路径（形如 `目标文件.part3`）。
     *
     * @param targetFile 最终目标文件。
     * @param chunkIndex 分片序号。
     * @return 分片临时文件（不保证已存在）。
     */
    fun partFile(targetFile: File, chunkIndex: Int): File =
        File(targetFile.parentFile, targetFile.name + PART_SUFFIX + chunkIndex)

    /**
     * 读取分片临时文件已落盘的大小，用于断点续传。
     *
     * @param targetFile 最终目标文件。
     * @param chunkIndex 分片序号。
     * @return 已落盘字节数；文件不存在时为 0。
     */
    fun readPartProgress(targetFile: File, chunkIndex: Int): Long {
        val part = partFile(targetFile, chunkIndex)
        return if (part.isFile) part.length() else 0L
    }

    /**
     * 把所有分片临时文件按序合并为目标文件。
     *
     * 实现说明：目标文件先被截断重建，再用追加流逐片拷贝，
     * 合并完成后删除全部分片临时文件。
     *
     * @param targetFile 最终目标文件。
     * @param chunks 分片列表（用于定位临时文件）。
     * @return 合并后的目标文件。
     * @throws IOException 读写失败时抛出，由调用方转成任务失败状态。
     */
    fun mergePartFiles(targetFile: File, chunks: List<Chunk>): File {
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) {
            targetFile.delete()
        }
        targetFile.outputStream().use { output ->
            for (chunk in chunks.sortedBy { item -> item.index }) {
                val part = partFile(targetFile, chunk.index)
                if (!part.isFile) {
                    Timber.e("Missing part file for chunk %d of %s", chunk.index, targetFile.name)
                    continue
                }
                part.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
        targetFile.setLastModified(System.currentTimeMillis())
        deletePartFiles(targetFile, chunks)
        return targetFile
    }

    /**
     * 判断目标文件是否还存在分片临时文件（【JYD-P1-2026-10-04】P1-2 续传可行性判定）。
     *
     * 实现说明：分片文件名形如 `目标名.part3`。判断方式是扫描目标文件所在目录，
     * 匹配「以目标文件名 + `.part` 开头、且其后全为数字」的条目——用逐项比较而非正则，
     * 避免目标文件名本身含 `.` 时正则转义出错。
     *
     * @param targetFile 最终目标文件。
     * @return true 表示至少存在一个分片临时文件。
     */
    fun hasPartFiles(targetFile: File): Boolean {
        val parent = targetFile.parentFile ?: return false
        val entries = parent.listFiles() ?: return false
        return partFilesOf(targetFile, entries).isNotEmpty()
    }

    /**
     * 从目录条目中筛出属于 [targetFile] 的分片临时文件。
     *
     * 说明：分片文件名形如 `目标名.part3`；用「前缀 + 其后全为数字」逐项比较而非正则，
     * 避免目标文件名本身含 `.` 时正则转义出错（【修订 JYD-DEBT5-2026-10-07】去重：
     * [hasPartFiles] 与 [deleteAllPartFiles] 原先各写一份同样的谓词）。
     *
     * @param targetFile 最终目标文件。
     * @param entries 已列出的目录条目。
     * @return 匹配到的分片临时文件列表。
     */
    private fun partFilesOf(targetFile: File, entries: Array<File>): List<File> {
        val prefix = targetFile.name + PART_SUFFIX
        return entries.filter { entry ->
            entry.isFile && entry.name.startsWith(prefix) &&
                entry.name.length > prefix.length &&
                entry.name.substring(prefix.length).all { symbol -> symbol.isDigit() }
        }
    }

    /**
     * 删除目标文件的全部分片临时文件（不论分片列表是否已知）。
     *
     * 说明（【JYD-P1-2026-10-04】P1-2「重新下载」）：重启后内存中的分片列表已丢失，
     * 无法用 [deletePartFiles] 逐片删除，故按文件名前缀扫描删除。
     *
     * @param targetFile 最终目标文件。
     * @return 实际删除的文件数量。
     */
    fun deleteAllPartFiles(targetFile: File): Int {
        val parent = targetFile.parentFile ?: return 0
        val entries = parent.listFiles() ?: return 0
        var removed = 0
        partFilesOf(targetFile, entries).forEach { entry ->
            if (entry.delete()) {
                removed++
            } else {
                Timber.e("Failed to delete part file: %s", entry.name)
            }
        }
        return removed
    }

    /**
     * 删除全部分片临时文件（取消任务或合并完成后调用）。
     *
     * @param targetFile 最终目标文件。
     * @param chunks 分片列表。
     */
    fun deletePartFiles(targetFile: File, chunks: List<Chunk>) {
        for (chunk in chunks) {
            val part = partFile(targetFile, chunk.index)
            if (part.isFile && !part.delete()) {
                Timber.e("Failed to delete part file: %s", part.name)
            }
        }
    }

    /**
     * 把任意分片数收敛到合法范围，并不超过文件字节数。
     *
     * @param raw 期望分片数。
     * @param fileSize 文件总大小（必须为正）。
     * @return 合法分片数。
     */
    private fun normalizeChunkCount(raw: Int, fileSize: Long): Int {
        val clamped = raw.coerceIn(DownloadTask.MIN_CHUNK_COUNT, DownloadTask.MAX_CHUNK_COUNT)
        return clamped.coerceAtMost(fileSize.toInt().coerceAtLeast(1))
    }

    companion object {
        /** 分片临时文件后缀。 */
        const val PART_SUFFIX = ".part"
    }
}