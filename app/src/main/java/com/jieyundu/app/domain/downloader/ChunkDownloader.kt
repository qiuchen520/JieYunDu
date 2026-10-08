// 文件：ChunkDownloader.kt
// 职责：单个分片的下载执行——Range 断点、有限重试、追加落盘、限速申请
// 依赖：OkHttpClient、Request、Chunk、DownloadTaskRuntime、SpeedLimiter、DownloadSettingsPort、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.downloader

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * 单分片下载器（【修订 JYD-DEBT3-2026-10-07】自 [DownloadEngine] 拆出）。
 *
 * 职责单一：把一个 [Chunk] 下载到它的 `.part` 文件——按断点构造 `Range` 头、有限次重试、
 * 追加落盘、按全局限速申请配额，并把「本次运行累计字节」回调给引擎用于进度发布。
 * 本类**不**持有进度状态、不落库、不管任务调度，因此可单独阅读与测试。
 *
 * @param okHttpClient 全局复用的 OkHttp 客户端。
 * @param speedLimiter 全局下载限速器（与其它任务 / 分片共享）。
 * @param settings 下载设置端口（读取当前限速）。
 */
internal class ChunkDownloader(
    private val okHttpClient: OkHttpClient,
    private val speedLimiter: SpeedLimiter,
    private val settings: DownloadSettingsPort
) {

    /**
     * 下载单个分片，内部带有限次重试。
     *
     * 说明：每轮重试都按临时文件**当前长度**重算断点（BUG-4-02），避免重复追加或覆盖已落盘数据。
     *
     * @param runtime 运行态任务。
     * @param chunk 分片描述。
     * @param partFile 该分片的临时文件。
     * @param onBytesRead 本次运行累计写入字节数的回调（引擎据此发布进度；本类不存进度）。
     * @throws IOException 重试耗尽后仍失败时抛出。
     */
    suspend fun download(
        runtime: DownloadTaskRuntime,
        chunk: Chunk,
        partFile: File,
        onBytesRead: (Long) -> Unit
    ) {
        var attempt = 0
        while (true) {
            // 每轮重试都按临时文件当前长度重算断点，避免重复追加或覆盖已有数据
            val alreadyDownloaded = if (partFile.isFile) partFile.length() else 0L
            val resumeFrom = chunk.start + alreadyDownloaded
            if (!chunk.isUnknownSize() && resumeFrom > chunk.end) {
                Timber.i("Chunk %d already completed, skip", chunk.index)
                return
            }

            val requestBuilder = Request.Builder()
                .url(runtime.task.url)
                .header(HEADER_RANGE, buildRangeHeader(chunk, resumeFrom))
                .get()
            runtime.task.headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            try {
                executeChunkRequest(runtime, requestBuilder.build(), partFile, onBytesRead)
                return
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (io: IOException) {
                attempt++
                if (attempt > MAX_RETRY_PER_CHUNK) {
                    Timber.e(io, "Chunk %d failed after %d attempts", chunk.index, attempt)
                    throw io
                }
                Timber.e(io, "Chunk %d retry %d/%d", chunk.index, attempt, MAX_RETRY_PER_CHUNK)
                delay(RETRY_DELAY_MILLIS)
            }
        }
    }

    /**
     * 构造 Range 请求头值。
     *
     * @param chunk 分片描述。
     * @param resumeFrom 本次请求的起始偏移（分片起点 + 已落盘字节数）。
     * @return 形如 `bytes=0-1023` 或开放式 `bytes=2048-` 的头值。
     */
    private fun buildRangeHeader(chunk: Chunk, resumeFrom: Long): String =
        if (chunk.isUnknownSize()) {
            "bytes=$resumeFrom-"
        } else {
            "bytes=$resumeFrom-${chunk.end}"
        }

    /**
     * 执行一次分片请求并把响应体追加写入分片临时文件。
     *
     * 说明（BUG-4-01）：落盘用**追加模式**，已存在的部分即断点，绝不能截断。
     *
     * @param runtime 运行态任务。
     * @param request 已带 Range 头的请求。
     * @param partFile 分片临时文件。
     * @param onBytesRead 累计写入字节数回调。
     * @throws IOException 连接失败、HTTP 非 2xx 或响应体为空。
     */
    private suspend fun executeChunkRequest(
        runtime: DownloadTaskRuntime,
        request: Request,
        partFile: File,
        onBytesRead: (Long) -> Unit
    ) {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP code ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            partFile.parentFile?.mkdirs()
            body.byteStream().use { input ->
                FileOutputStream(partFile, true).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                        // C1：按全局限速申请配额；不限速时立即返回。
                        speedLimiter.acquire(read, settings.currentSpeedLimitBytesPerSecond())
                        val sessionTotal = runtime.sessionBytes.addAndGet(read.toLong())
                        onBytesRead(sessionTotal)
                    }
                    output.flush()
                }
            }
        }
    }

    private companion object {
        /** Range 请求头名。 */
        private const val HEADER_RANGE = "Range"

        /** 单次读取缓冲区大小：64 KiB。 */
        private const val BUFFER_SIZE_BYTES = 64 * 1024

        /** 单分片最大重试次数。 */
        private const val MAX_RETRY_PER_CHUNK = 3

        /** 重试等待时长。 */
        private const val RETRY_DELAY_MILLIS = 1500L
    }
}
