// 文件：DownloadEngine.kt
// 职责：分片并发下载引擎，提供启动/暂停/继续/取消与状态流
// 依赖：OkHttpClient、ChunkManager、Chunk、DownloadTask、DownloadState、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.downloader

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * 分片并发下载引擎。
 *
 * 能力（《要求.md》4.2）：
 * - HTTP Range 分片并发，分片数即并发数，收敛到 1..32；
 * - 断点续传：每个分片落盘为独立 `.part` 文件，重启后按已落盘长度续传；
 * - 暂停 / 继续 / 取消；
 * - 通过 [observe] 暴露离散状态，通过 [observeProgress] 暴露进度与速度。
 *
 * 线程约束：所有下载都在引擎自有的 [Dispatchers.IO] 作用域内执行（C9）；
 * 不使用 GlobalScope（D13），不吞 `CancellationException`（C3）。
 *
 * 签名说明（依据【修订 JYD-ERRATA-2026-10-03】修订一）：《要求.md》7.5 的第二个
 * 构造参数类型为 [DownloadProgressPort]（端口方案，避免 domain 反向依赖 data 层），
 * 参数名保留 `downloadDao`；阶段 5 起由 Room 的 `DownloadDao` 实现该端口。
 *
 * @param okHttpClient 全局复用的 OkHttp 客户端（超时配置见 di/NetworkModule）。
 * @param downloadDao 进度落库端口，见类注释。
 */
@Singleton
class DownloadEngine @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val downloadDao: DownloadProgressPort
) {

    /** 引擎自有作用域，随进程存活；不使用 GlobalScope。 */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 分片计算与文件合并工具。 */
    private val chunkManager: ChunkManager = ChunkManager()

    /** 运行中的任务表。 */
    private val runtimes: MutableMap<String, TaskRuntime> = ConcurrentHashMap()

    /**
     * 启动一个下载任务。
     *
     * 说明：若任务已在下载中则直接返回；若存在残留的 `.part` 文件则自动续传。
     *
     * @param task 运行态任务描述。
     */
    suspend fun start(task: DownloadTask) {
        val existing = runtimes[task.taskId]
        if (existing != null && existing.state.value == DownloadState.DOWNLOADING) {
            Timber.i("DownloadEngine start ignored: task %s is already running", task.taskId)
            return
        }

        val targetFile = File(task.savePath)
        val requestedCount = task.chunkCount.coerceIn(
            DownloadTask.MIN_CHUNK_COUNT,
            DownloadTask.MAX_CHUNK_COUNT
        )
        val chunks = chunkManager.createChunks(task.fileSize, requestedCount).map { chunk ->
            chunk.withDownloaded(chunkManager.readPartProgress(targetFile, chunk.index))
        }

        val runtime = runtimes.getOrPut(task.taskId) { TaskRuntime(task, chunks.size) }
        runtime.chunks = chunks
        runtime.sessionBytes.set(0L)
        runtime.initialBytes = chunks.sumOf { chunk -> chunk.downloadedBytes }
        runtime.lastSpeedSampleAt = System.currentTimeMillis()
        runtime.lastSpeedSampleBytes = runtime.initialBytes
        runtime.lastEmitAt = 0L
        runtime.state.value = DownloadState.DOWNLOADING
        runtime.progress.value = DownloadProgressState(
            taskId = task.taskId,
            state = DownloadState.DOWNLOADING,
            downloadedBytes = runtime.initialBytes,
            totalBytes = task.fileSize,
            speedBytesPerSecond = 0L,
            chunkCount = chunks.size,
            completedChunks = chunks.count { chunk -> chunk.isCompleted },
            savePath = task.savePath
        )
        // 【修订 JYD-SAVEPATH-2026-10-03】启动即落库：既让新任务立即出现在下载列表，
        // 也把落盘路径写入持久层，供进程重启后「删除本地文件」定位目标。
        downloadDao.upsert(runtime.progress.value)
        runtime.job = scope.launch { runTask(runtime) }
    }

    /**
     * 暂停任务。
     *
     * 已落盘的分片不会被删除，下一步 [resume] 可续传。
     *
     * @param taskId 任务 ID；不存在时静默返回。
     */
    suspend fun pause(taskId: String) {
        val runtime = runtimes[taskId]
        if (runtime == null) {
            Timber.e("DownloadEngine pause failed: task %s not found", taskId)
            return
        }
        runtime.job?.cancel()
        runtime.job = null
        runtime.state.value = DownloadState.PAUSED
        runtime.progress.value = runtime.progress.value.copy(
            state = DownloadState.PAUSED,
            speedBytesPerSecond = 0L
        )
        downloadDao.upsert(runtime.progress.value)
        Timber.i("DownloadEngine paused task %s", taskId)
    }

    /**
     * 继续任务。
     *
     * @param taskId 任务 ID；对应的运行态任务不存在时无法恢复（错误日志提示）。
     */
    suspend fun resume(taskId: String) {
        val runtime = runtimes[taskId]
        if (runtime == null) {
            Timber.e("DownloadEngine resume failed: task %s not found", taskId)
            return
        }
        if (runtime.state.value == DownloadState.DOWNLOADING) {
            return
        }
        val persisted = downloadDao.query(taskId)
        if (persisted != null) {
            Timber.i(
                "DownloadEngine resume task %s with persisted bytes %d",
                taskId,
                persisted.downloadedBytes
            )
        }
        start(runtime.task)
    }

    /**
     * 取消任务：终止协程、删除全部分片临时文件与目标文件。
     *
     * @param taskId 任务 ID；不存在时静默返回。
     */
    suspend fun cancel(taskId: String) {
        val runtime = runtimes.remove(taskId)
        if (runtime == null) {
            Timber.e("DownloadEngine cancel failed: task %s not found", taskId)
            return
        }
        runtime.job?.cancel()
        runtime.job = null
        runtime.state.value = DownloadState.CANCELED
        val targetFile = File(runtime.task.savePath)
        chunkManager.deletePartFiles(targetFile, runtime.chunks)
        if (targetFile.isFile && !targetFile.delete()) {
            Timber.e("DownloadEngine failed to delete target file: %s", targetFile.name)
        }
        downloadDao.delete(taskId)
        runtime.progress.value = runtime.progress.value.copy(
            state = DownloadState.CANCELED,
            downloadedBytes = 0L,
            speedBytesPerSecond = DownloadProgressState.UNKNOWN_SIZE
        )
        Timber.i("DownloadEngine canceled task %s", taskId)
    }

    /**
     * 观察任务状态流。
     *
     * 注意：返回的 Flow 基于 `StateFlow`，**不会**自行结束；调用方需在
     * 生命周期结束时取消收集（符合 C3 的取消语义）。
     *
     * @param taskId 任务 ID。
     * @return 状态流；任务尚未启动时返回只发一次 [DownloadState.PENDING] 的流。
     */
    fun observe(taskId: String): Flow<DownloadState> =
        runtimes[taskId]?.state?.asStateFlow() ?: flowOf(DownloadState.PENDING)

    /**
     * 观察任务进度流（界面显示速度与百分比用）。
     *
     * @param taskId 任务 ID。
     * @return 进度流；任务尚未启动时返回初始快照。
     */
    fun observeProgress(taskId: String): Flow<DownloadProgressState> =
        runtimes[taskId]?.progress?.asStateFlow()
            ?: flowOf(DownloadProgressState.initial(taskId, DownloadTask.DEFAULT_CHUNK_COUNT))

    /**
     * 执行任务主体：并发下载全部分片 → 合并 → 落库。
     *
     * @param runtime 运行态任务。
     */
    private suspend fun runTask(runtime: TaskRuntime) {
        val targetFile = File(runtime.task.savePath)
        val chunks = runtime.chunks
        try {
            coroutineScope {
                chunks.map { chunk ->
                    async {
                        downloadChunk(runtime, chunk, chunkManager.partFile(targetFile, chunk.index))
                    }
                }.awaitAll()
            }

            withContext(Dispatchers.IO) {
                chunkManager.mergePartFiles(targetFile, chunks)
            }

            runtime.state.value = DownloadState.COMPLETED
            runtime.progress.value = runtime.progress.value.copy(
                state = DownloadState.COMPLETED,
                downloadedBytes = if (runtime.task.hasKnownSize) {
                    runtime.task.fileSize
                } else {
                    runtime.progress.value.downloadedBytes
                },
                speedBytesPerSecond = 0L,
                completedChunks = chunks.size
            )
            downloadDao.upsert(runtime.progress.value)
            Timber.i("DownloadEngine completed task %s", runtime.task.taskId)
        } catch (cancellation: CancellationException) {
            // C3：取消必须原样抛出，不能转成 FAILED
            throw cancellation
        } catch (io: IOException) {
            Timber.e(io, "DownloadEngine task %s failed with IO error", runtime.task.taskId)
            runtime.state.value = DownloadState.FAILED
            runtime.progress.value = runtime.progress.value.copy(
                state = DownloadState.FAILED,
                speedBytesPerSecond = DownloadProgressState.UNKNOWN_SIZE
            )
        } catch (exception: Exception) {
            Timber.e(exception, "DownloadEngine task %s failed", runtime.task.taskId)
            runtime.state.value = DownloadState.FAILED
            runtime.progress.value = runtime.progress.value.copy(
                state = DownloadState.FAILED,
                speedBytesPerSecond = DownloadProgressState.UNKNOWN_SIZE
            )
        }
    }

    /**
     * 下载单个分片，内部带有限次重试。
     *
     * @param runtime 运行态任务。
     * @param chunk 分片描述。
     * @param partFile 该分片的临时文件。
     * @throws IOException 重试耗尽后仍失败时抛出。
     */
    private suspend fun downloadChunk(
        runtime: TaskRuntime,
        chunk: Chunk,
        partFile: File
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
                executeChunkRequest(runtime, requestBuilder.build(), partFile)
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
     * @param runtime 运行态任务。
     * @param request 已带 Range 头的请求。
     * @param partFile 分片临时文件。
     * @throws IOException 连接失败、HTTP 非 2xx 或响应体为空。
     */
    private suspend fun executeChunkRequest(
        runtime: TaskRuntime,
        request: Request,
        partFile: File
    ) {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP code ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            partFile.parentFile?.mkdirs()
            body.byteStream().use { input ->
                // 追加写入：分片临时文件已存在的部分即为断点，绝不能截断
                FileOutputStream(partFile, true).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                        val sessionTotal = runtime.sessionBytes.addAndGet(read.toLong())
                        publishProgress(runtime, sessionTotal)
                    }
                    output.flush()
                }
            }
        }
    }

    /**
     * 以节流方式刷新进度快照并计算瞬时速度。
     *
     * @param runtime 运行态任务。
     * @param sessionBytes 本次运行累计写入的字节数。
     */
    private fun publishProgress(runtime: TaskRuntime, sessionBytes: Long) {
        val now = System.currentTimeMillis()
        if (now - runtime.lastEmitAt < PROGRESS_INTERVAL_MILLIS) {
            return
        }
        runtime.lastEmitAt = now

        val downloaded = runtime.initialBytes + sessionBytes
        val elapsed = now - runtime.lastSpeedSampleAt
        val speed = if (elapsed > 0L) {
            (downloaded - runtime.lastSpeedSampleBytes) * 1000L / elapsed
        } else {
            DownloadProgressState.UNKNOWN_SIZE
        }
        runtime.lastSpeedSampleAt = now
        runtime.lastSpeedSampleBytes = downloaded

        runtime.progress.value = runtime.progress.value.copy(
            state = runtime.state.value,
            downloadedBytes = downloaded,
            totalBytes = if (runtime.task.hasKnownSize) {
                runtime.task.fileSize
            } else {
                DownloadProgressState.UNKNOWN_SIZE
            },
            speedBytesPerSecond = speed
        )
    }

    /**
     * 单个任务的运行态数据。
     *
     * @param task 任务描述。
     * @param chunkCount 分片数，用于构造初始进度快照。
     */
    private class TaskRuntime(
        val task: DownloadTask,
        chunkCount: Int
    ) {
        /** 离散状态。 */
        val state: MutableStateFlow<DownloadState> = MutableStateFlow(DownloadState.PENDING)

        /** 进度快照。 */
        val progress: MutableStateFlow<DownloadProgressState> =
            MutableStateFlow(DownloadProgressState.initial(task.taskId, chunkCount))

        /** 当前任务协程句柄。 */
        var job: Job? = null

        /** 当前分片列表。 */
        @Volatile
        var chunks: List<Chunk> = emptyList()

        /** 本次运行累计写入字节数（原子，多个分片协程并发写入）。 */
        val sessionBytes: AtomicLong = AtomicLong(0L)

        /** 本次运行开始前已落盘的字节数（续传起点）。 */
        @Volatile
        var initialBytes: Long = 0L

        /** 上一次进度发射时间戳，用于节流。 */
        @Volatile
        var lastEmitAt: Long = 0L

        /** 上一次测速采样时间戳。 */
        @Volatile
        var lastSpeedSampleAt: Long = 0L

        /** 上一次测速采样时的字节数。 */
        @Volatile
        var lastSpeedSampleBytes: Long = 0L
    }

    private companion object {
        /** Range 请求头名。 */
        const val HEADER_RANGE = "Range"

        /** 单次读取缓冲区大小：64 KiB。 */
        const val BUFFER_SIZE_BYTES = 64 * 1024

        /** 单分片最大重试次数。 */
        const val MAX_RETRY_PER_CHUNK = 3

        /** 重试等待时长。 */
        const val RETRY_DELAY_MILLIS = 1500L

        /** 进度发射节流间隔。 */
        const val PROGRESS_INTERVAL_MILLIS = 200L
    }
}

/**
 * 下载进度落库端口（依据【修订 JYD-ERRATA-2026-10-03】修订一）。
 *
 * 端口定义置于本文件内（不新增文件）；阶段 5 起由 Room 的 `DownloadDao` 实现，
 * 从而在保持 domain 层不反向依赖 data 层的前提下完成进度持久化。
 */
interface DownloadProgressPort {

    /**
     * 写入或更新一条下载进度。
     *
     * @param progress 进度快照（含任务 ID）。
     */
    suspend fun upsert(progress: DownloadProgressState)

    /**
     * 查询指定任务的下载进度。
     *
     * @param taskId 任务 ID。
     * @return 进度快照；无记录时返回 null。
     */
    suspend fun query(taskId: String): DownloadProgressState?

    /**
     * 删除指定任务的下载进度记录。
     *
     * @param taskId 任务 ID。
     */
    suspend fun delete(taskId: String)
}