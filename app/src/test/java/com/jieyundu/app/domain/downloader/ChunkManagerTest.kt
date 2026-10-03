// 文件：ChunkManagerTest.kt
// 职责：分片切分数学与分片合并的单元测试
// 依赖：ChunkManager、Chunk、kotlin.test
// 协议：AGPL-3.0

package com.jieyundu.app.domain.downloader

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ChunkManager] 的单元测试。
 *
 * 覆盖点：
 * 1. 分片区间连续、无重叠、无空洞，且完整覆盖 `[0, fileSize-1]`；
 * 2. 分片数收敛规则（1..32，且不超过文件字节数）；
 * 3. 大小未知时退化为单个开放式分片；
 * 4. 分片临时文件命名与合并结果正确、合并后临时文件被清理。
 */
class ChunkManagerTest {

    private val manager = ChunkManager()

    /** 1000 字节切 8 片：数量、连续性、覆盖率都正确。 */
    @Test
    fun createChunks_evenSplit_coversWholeFile() {
        val fileSize = 1000L
        val chunks = manager.createChunks(fileSize, 8)

        assertEquals(8, chunks.size)
        assertEquals(0L, chunks.first().start)
        assertEquals(fileSize - 1L, chunks.last().end)

        var expectedStart = 0L
        for (chunk in chunks) {
            assertEquals(expectedStart, chunk.start, "chunk ${chunk.index} start mismatch")
            assertTrue(chunk.end >= chunk.start, "chunk ${chunk.index} end before start")
            expectedStart = chunk.end + 1L
        }
        assertEquals(fileSize, expectedStart, "chunks do not cover the whole file")

        val totalBytes = chunks.sumOf { chunk -> chunk.totalBytes }
        assertEquals(fileSize, totalBytes)
    }

    /** 余数应逐片 +1 分配，不丢字节。 */
    @Test
    fun createChunks_withRemainder_doesNotLoseBytes() {
        val fileSize = 1003L
        val chunks = manager.createChunks(fileSize, 8)

        assertEquals(8, chunks.size)
        val sizes = chunks.map { chunk -> chunk.totalBytes }
        assertEquals(3, sizes.count { size -> size == 126L })
        assertEquals(5, sizes.count { size -> size == 125L })
        assertEquals(fileSize, sizes.sum())
    }

    /** 分片数超过上限时应被收敛到 32。 */
    @Test
    fun createChunks_aboveMax_isClampedTo32() {
        val chunks = manager.createChunks(1024L * 1024L, 999)
        assertEquals(DownloadTask.MAX_CHUNK_COUNT, chunks.size)
    }

    /** 分片数为 0 或负数时应被收敛到 1。 */
    @Test
    fun createChunks_belowMin_isClampedToOne() {
        assertEquals(1, manager.createChunks(1024L, 0).size)
        assertEquals(1, manager.createChunks(1024L, -5).size)
    }

    /** 文件字节数小于分片数时，分片数不应超过字节数。 */
    @Test
    fun createChunks_tinyFile_chunkCountNotGreaterThanFileSize() {
        val chunks = manager.createChunks(3L, 8)
        assertEquals(3, chunks.size)
        assertEquals(0L, chunks[0].start)
        assertEquals(2L, chunks[2].end)
    }

    /** 文件大小未知时，返回单个开放式分片。 */
    @Test
    fun createChunks_unknownSize_returnsSingleOpenChunk() {
        val chunks = manager.createChunks(-1L, 8)
        assertEquals(1, chunks.size)
        assertTrue(chunks.first().isUnknownSize())
        assertEquals(Chunk.UNKNOWN_SIZE, chunks.first().totalBytes)
    }

    /** 分片临时文件名应为“目标文件名 + .part + 序号”。 */
    @Test
    fun partFile_hasExpectedName() {
        val target = File("/tmp/demo/video.mp4")
        val part = manager.partFile(target, 3)
        assertEquals("video.mp4.part3", part.name)
        assertEquals(target.parentFile, part.parentFile)
    }

    /** 合并后内容为分片顺序拼接，且临时分片被删除。 */
    @Test
    fun mergePartFiles_concatenatesInOrderAndCleansUp() {
        val directory = Files.createTempDirectory("chunkmanager-test").toFile()
        val target = File(directory, "merged.bin")
        val chunks = manager.createChunks(9L, 3)
        assertEquals(3, chunks.size)

        val contents = listOf("AAA", "BBB", "CCC")
        chunks.forEachIndexed { index, chunk ->
            manager.partFile(target, chunk.index).writeText(contents[index])
        }

        val merged = manager.mergePartFiles(target, chunks)

        assertTrue(merged.isFile)
        assertEquals("AAABBBCCC", merged.readText())
        chunks.forEach { chunk ->
            assertFalse(manager.partFile(target, chunk.index).exists(), "part file not deleted")
        }
        directory.delete()
    }

    /** 读取分片进度：文件不存在返回 0，存在时返回文件长度。 */
    @Test
    fun readPartProgress_returnsFileLength() {
        val directory = Files.createTempDirectory("chunkmanager-progress").toFile()
        val target = File(directory, "progress.bin")

        assertEquals(0L, manager.readPartProgress(target, 0))

        manager.partFile(target, 0).writeText("12345")
        assertEquals(5L, manager.readPartProgress(target, 0))

        directory.deleteRecursively()
    }
}
