// 文件：DownloadEntityTest.kt
// 职责：下载实体「任务存档 / 请求头编解码」的单元测试（P1-1 · P1-2 · JYD-P1-2026-10-04）
// 依赖：JUnit4、DownloadEntity、DownloadTaskRecord、DownloadState
// 协议：AGPL-3.0

package com.jieyundu.app.data.local

import com.jieyundu.app.domain.downloader.DownloadState
import com.jieyundu.app.domain.downloader.DownloadTaskRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DownloadEntity] 的单元测试。
 *
 * 重点覆盖 P1-1 / P1-2 依赖的两件事：
 * 1. 任务名与直链能随「任务存档」写入并读回（重启后据此显示名字、继续下载）；
 * 2. 请求头能往返编解码（Cookie / Referer 这类含 `:` 与 `=` 的值不能丢）。
 */
class DownloadEntityTest {

    private companion object {
        /** 测试用任务存档。 */
        val RECORD = DownloadTaskRecord(
            taskId = "task-1",
            url = "https://example.com/file?x=1",
            fileName = "电影.mp4",
            fileSize = 1024L,
            savePath = "/data/user/0/app/files/download/电影.mp4",
            chunkCount = 64,
            headers = mapOf(
                "Cookie" to "__pus=abc; __puus=def",
                "Referer" to "https://drive.uc.cn/",
                "User-Agent" to "Mozilla/5.0 (Linux; Android 14)"
            )
        )
    }

    @Test
    fun `task record survives round trip`() {
        val entity = DownloadEntity.fromTaskRecord(
            record = RECORD,
            state = DownloadState.PAUSED.name,
            downloadedBytes = 512L
        )
        val restored = entity.toTaskRecord()

        assertNotNull(restored)
        requireNotNull(restored)
        assertEquals(RECORD.taskId, restored.taskId)
        assertEquals(RECORD.url, restored.url)
        assertEquals(RECORD.fileName, restored.fileName)
        assertEquals(RECORD.fileSize, restored.fileSize)
        assertEquals(RECORD.savePath, restored.savePath)
        assertEquals(RECORD.chunkCount, restored.chunkCount)
        assertEquals(RECORD.headers, restored.headers)
    }

    @Test
    fun `task record returns null when direct link missing`() {
        val entity = DownloadEntity.fromTaskRecord(
            record = RECORD,
            state = DownloadState.PAUSED.name,
            downloadedBytes = 0L
        ).copy(url = null)

        assertNull(entity.toTaskRecord())
    }

    @Test
    fun `task record returns null when save path missing`() {
        val entity = DownloadEntity.fromTaskRecord(
            record = RECORD,
            state = DownloadState.PAUSED.name,
            downloadedBytes = 0L
        ).copy(savePath = null)

        assertNull(entity.toTaskRecord())
    }

    @Test
    fun `header codec keeps values containing colon and equals`() {
        val encoded = DownloadEntity.encodeHeaders(
            mapOf("Cookie" to "__pus=abc; __puus=def", "Referer" to "https://drive.uc.cn/")
        )
        assertNotNull(encoded)
        val decoded = DownloadEntity.decodeHeaders(encoded)

        assertEquals("__pus=abc; __puus=def", decoded["Cookie"])
        assertEquals("https://drive.uc.cn/", decoded["Referer"])
    }

    @Test
    fun `header codec drops blank entries and returns null for empty map`() {
        assertNull(DownloadEntity.encodeHeaders(emptyMap()))
        assertNull(DownloadEntity.encodeHeaders(mapOf("Cookie" to "   ")))
        assertTrue(DownloadEntity.decodeHeaders(null).isEmpty())
        assertTrue(DownloadEntity.decodeHeaders("").isEmpty())
    }

    @Test
    fun `header codec ignores malformed lines`() {
        val decoded = DownloadEntity.decodeHeaders("Cookie: a=b\nmalformed-line\nReferer: https://x/")

        assertEquals(2, decoded.size)
        assertEquals("a=b", decoded["Cookie"])
        assertEquals("https://x/", decoded["Referer"])
    }
}
