// 文件：DownloadEngineTest.kt
// 职责：DownloadEngine 的行为单测（完成 / 断点续传 / 重启续传 / 重新下载 / 无存档）
// 依赖：JUnit4、OkHttp（假响应拦截器，不触网）、协程
// 协议：AGPL-3.0

package com.jieyundu.app.domain.downloader

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DownloadEngine] 行为单测（【JYD-DEBT1-2026-10-05】补债）。
 *
 * 背景：引擎是本项目最大的单文件（约 1000 行）却**长期零单测**；分片 / 续传 / 重下
 * 这些「一旦写错就悄悄重下或续错位」的行为此前无任何自动化保护。本测试用
 * **假 OkHttp 响应**（拦截器直接返回固定响应体，不触网、不依赖 MockWebServer），
 * 覆盖四条主路径，并用 **Range 请求头**作为「是否真的从断点续传」的直接证据。
 */
class DownloadEngineTest {

    /** 记录引擎实际发出的 Range 头（断点续传判据）。 */
    private val rangeHeaders = mutableListOf<String>()

    /**
     * 构造一个「总是返回 [body]」的假客户端，并记录每个请求的 Range 头。
     *
     * @param body 响应体内容。
     * @return 假 OkHttp 客户端。
     */
    private fun fakeClient(body: String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            rangeHeaders += chain.request().header("Range").orEmpty()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/octet-stream".toMediaType()))
                .build()
        })
        .build()

    /** 进度端口替身（记录落库快照）。 */
    private class FakeProgressPort : DownloadProgressPort {
        val upserts = mutableListOf<DownloadProgressState>()
        override suspend fun upsert(progress: DownloadProgressState) {
            upserts += progress
        }

        override suspend fun query(taskId: String): DownloadProgressState? =
            upserts.lastOrNull { state -> state.taskId == taskId }

        override suspend fun delete(taskId: String) {
            upserts.removeAll { state -> state.taskId == taskId }
        }
    }

    /** 任务存档端口替身（可读回一条预设存档）。 */
    private class FakeCheckpoint(private val stored: DownloadTaskRecord?) : DownloadCheckpointPort {
        var saved: DownloadTaskRecord? = null
        override suspend fun upsertTask(record: DownloadTaskRecord) {
            saved = record
        }

        override suspend fun loadTask(taskId: String): DownloadTaskRecord? =
            stored?.takeIf { record -> record.taskId == taskId }
    }

    /** 设置端口替身：单任务并发、不限速、不重试（让失败快速落到终态）。 */
    private class FakeSettings : DownloadSettingsPort {
        override fun currentMaxConcurrentTasks(): Int = 1
        override fun currentSpeedLimitBytesPerSecond(): Long = 0L
        override fun currentMaxTaskRetries(): Int = 0
    }

    /** 等待任务进入终态，返回最终状态。 */
    private suspend fun awaitFinal(engine: DownloadEngine, taskId: String): DownloadState =
        withTimeout(AWAIT_TIMEOUT_MILLIS) {
            engine.observe(taskId).first { state ->
                state == DownloadState.COMPLETED ||
                    state == DownloadState.FAILED ||
                    state == DownloadState.CANCELED
            }
        }

    @Test
    fun `downloads unknown size file and persists task name`() = runBlocking {
        val dir = Files.createTempDirectory("jyd-engine").toFile()
        val target = File(dir, "movie.bin")
        val checkpoint = FakeCheckpoint(null)
        val progress = FakeProgressPort()
        val engine = DownloadEngine(fakeClient("hello"), progress, checkpoint, FakeSettings())
        val task = DownloadTask(
            taskId = "task-1",
            url = "https://example.invalid/f",
            fileName = "movie.bin",
            fileSize = -1L,
            savePath = target.absolutePath,
            chunkCount = DownloadTask.MIN_CHUNK_COUNT
        )

        engine.start(task)
        assertEquals(DownloadState.COMPLETED, awaitFinal(engine, task.taskId))
        assertEquals("hello", target.readText())
        // P1-1 回归：引擎建任务时必须把任务名写入存档，否则重启后又变「未命名」。
        assertEquals("movie.bin", checkpoint.saved?.fileName)
    }

    @Test
    fun `resume after process restart continues from existing part file`() = runBlocking {
        val dir = Files.createTempDirectory("jyd-engine").toFile()
        val target = File(dir, "movie.bin")
        // 预置断点：分片 0 已有 2 字节。
        File(dir, "movie.bin.part0").writeText("AB")

        val record = DownloadTaskRecord(
            taskId = "task-2",
            url = "https://example.invalid/f",
            fileName = "movie.bin",
            fileSize = -1L,
            savePath = target.absolutePath,
            chunkCount = DownloadTask.MIN_CHUNK_COUNT,
            headers = emptyMap()
        )
        val engine = DownloadEngine(
            fakeClient("C"),
            FakeProgressPort(),
            FakeCheckpoint(record),
            FakeSettings()
        )

        // 引擎内存中**没有**该任务（等价于进程重启后的状态）。
        val result = engine.resume("task-2")

        assertEquals(EngineActionResult.Resumed, result)
        assertEquals(DownloadState.COMPLETED, awaitFinal(engine, "task-2"))
        // 证据一：Range 从断点 2 起，而不是从 0 重下。
        assertTrue("应请求 bytes=2-，实际=$rangeHeaders", rangeHeaders.any { it == "bytes=2-" })
        // 证据二：旧分片内容被保留并拼接新内容。
        assertEquals("ABC", target.readText())
    }

    @Test
    fun `resume reports missing part files when nothing on disk`() = runBlocking {
        val dir = Files.createTempDirectory("jyd-engine").toFile()
        val target = File(dir, "gone.bin")
        val record = DownloadTaskRecord(
            taskId = "task-3",
            url = "https://example.invalid/f",
            fileName = "gone.bin",
            fileSize = 100L,
            savePath = target.absolutePath,
            chunkCount = DownloadTask.MIN_CHUNK_COUNT,
            headers = emptyMap()
        )
        val engine = DownloadEngine(
            fakeClient("x"),
            FakeProgressPort(),
            FakeCheckpoint(record),
            FakeSettings()
        )

        // 分片不存在 → 必须明确告知「文件已损坏，请重新下载」，而不是静默从头下。
        assertEquals(EngineActionResult.MissingPartFiles, engine.resume("task-3"))
        assertTrue(rangeHeaders.isEmpty())
    }

    @Test
    fun `resume without checkpoint reports no record`() = runBlocking {
        val engine = DownloadEngine(
            fakeClient("x"),
            FakeProgressPort(),
            FakeCheckpoint(null),
            FakeSettings()
        )
        assertEquals(EngineActionResult.NoCheckpoint, engine.resume("task-4"))
    }

    @Test
    fun `restart clears part files and downloads from zero`() = runBlocking {
        val dir = Files.createTempDirectory("jyd-engine").toFile()
        val target = File(dir, "again.bin")
        val part = File(dir, "again.bin.part0")
        part.writeText("STALE")

        val record = DownloadTaskRecord(
            taskId = "task-5",
            url = "https://example.invalid/f",
            fileName = "again.bin",
            fileSize = -1L,
            savePath = target.absolutePath,
            chunkCount = DownloadTask.MIN_CHUNK_COUNT,
            headers = emptyMap()
        )
        val engine = DownloadEngine(
            fakeClient("NEW"),
            FakeProgressPort(),
            FakeCheckpoint(record),
            FakeSettings()
        )

        assertEquals(EngineActionResult.Restarted, engine.restart("task-5"))
        assertEquals(DownloadState.COMPLETED, awaitFinal(engine, "task-5"))
        // 「重新下载」必须从 0 开始，且不残留旧分片内容。
        assertTrue("应从 bytes=0- 开始，实际=$rangeHeaders", rangeHeaders.any { it == "bytes=0-" })
        assertEquals("NEW", target.readText())
    }

    private companion object {
        /** 等待终态的超时（毫秒）。 */
        const val AWAIT_TIMEOUT_MILLIS = 10_000L
    }
}
