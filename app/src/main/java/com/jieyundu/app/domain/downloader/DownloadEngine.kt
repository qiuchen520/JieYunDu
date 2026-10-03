// 文件：DownloadEngine.kt
// 职责：分片并发下载引擎，提供启动/暂停/继续/取消与状态流
// 依赖：OkHttpClient、ChunkManager、Chunk、DownloadTask、DownloadState、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.downloader

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * 分片并发下载引擎。
 *
 * 能力（《要求.md》4.2）：
 * - HTTP Range 分片并发，分片数即并发数，收敛到 32..512（B2 功能②）；
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
    private val downloadDao: DownloadProgressPort,
    private val settings: DownloadSettingsPort
) {

    /** 引擎自有作用域，随进程存活；不使用 GlobalScope。 */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /**
     * 分片下载专用调度器（B2 功能②）。
     *
     * 背景：分片下载走阻塞式 [okhttp3.Call.execute]，真实并发度受「同时阻塞的线程数」限制，
     * 而 [Dispatchers.IO] 的并行度上限为 64，无法支撑 32–512 档位。故改用按需创建、空闲回收的
     * 缓存线程池：分片数即并发数，配合 [ChunkManager] 切分出多少个分片就同时跑多少条阻塞请求。
     * 空闲线程在回收期内自动销毁，不会长期占用资源。
     */
    private val downloadDispatcher = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, DOWNLOAD_THREAD_NAME)
    }.asCoroutineDispatcher()
    /** 分片计算与文件合并工具。 */
    private val chunkManager: ChunkManager = ChunkManager()

    /** 运行中的任务表。 */
    private val runtimes: MutableMap<String, TaskRuntime> = ConcurrentHashMap()

    /** 任务调度锁（C1：并发闸门；保护 [runningTaskCount] 与 [pendingQueue]）。 */
    private val scheduleMutex = Mutex()

    /** 等待执行的任务 ID 队列（FIFO，C1）。 */
    private val pendingQueue: ArrayDeque<String> = ArrayDeque()

    /** 当前正在执行的任务数（C1，受 [scheduleMutex] 保护）。 */
    private var runningTaskCount: Int = 0

    /** 全局下载限速器（C1：所有任务与其分片共享一个令牌桶）。 */
    private val speedLimiter = SpeedLimiter()

    /**
     * 启动一个下载任务。
     *
     * 说明：若任务已在下载中则直接返回；若存在残留的 `.part` 文件则自动续传。
     *
     * @param task 运行态任务描述。
     */
    suspend fun start(task: DownloadTask) {
        val existing = runtimes[task.taskId]
        if (existing != null && existing.state.value in ACTIVE_STATES) {
            Timber.i("DownloadEngine start ignored: task %s is already active", task.taskId)
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
        // C1：不直接启动，交给调度器按「最大同时下载任务数」排队 / 放行。
        schedule(runtime)
    }

    /**
     * 任务调度闸门（C1）：按「最大同时下载任务数」决定立即执行还是排队。
     *
     * 若当前运行任务数未达上限则占用一个槽位并立即启动；否则入 [pendingQueue] 等待，
     * 状态置为 [DownloadState.PENDING]。任一任务结束时释放槽位并从队列头部补位。
     *
     * @param runtime 运行态任务。
     */
    private suspend fun schedule(runtime: TaskRuntime) {
        val maxConcurrent = settings.currentMaxConcurrentTasks()
            .coerceIn(DownloadTask.MIN_MAX_CONCURRENT_TASKS, DownloadTask.MAX_MAX_CONCURRENT_TASKS)
        val admitted = scheduleMutex.withLock {
            if (runningTaskCount < maxConcurrent) {
                runningTaskCount++
                true
            } else {
                pendingQueue.addLast(runtime.task.taskId)
                false
            }
        }
        if (admitted) {
            runtime.state.value = DownloadState.DOWNLOADING
            launchTask(runtime)
        } else {
            runtime.state.value = DownloadState.PENDING
            Timber.i("DownloadEngine queued task %s (max=%d)", runtime.task.taskId, maxConcurrent)
        }
    }

    /**
     * 在引擎作用域内启动任务协程，并在结束时释放调度槽位（C1）。
     *
     * 说明：`finally` 中的槽位释放包在 [NonCancellable] 里，确保任务即使被取消也能补位下一任务。
     *
     * @param runtime 运行态任务。
     */
    private fun launchTask(runtime: TaskRuntime) {
        runtime.job = scope.launch {
            try {
                runTask(runtime)
            } finally {
                withContext(NonCancellable) {
                    releaseSlotAndDispatchNext()
                }
            }
        }
    }

    /**
     * 释放一个运行槽位，并从等待队列中补位下一个任务（C1）。
     *
     * 队列中的任务可能已被取消或暂停，因此逐个出队并校验状态，
     * 仅对仍处于 [DownloadState.PENDING] 的任务补位。
     */
    private suspend fun releaseSlotAndDispatchNext() {
        val nextId = scheduleMutex.withLock {
            runningTaskCount = (runningTaskCount - 1).coerceAtLeast(0)
            var candidate: String? = null
            while (candidate == null && pendingQueue.isNotEmpty()) {
                val id = pendingQueue.removeFirst()
                val queued = runtimes[id]
                if (queued != null && queued.state.value == DownloadState.PENDING) {
                    runningTaskCount++
                    candidate = id
                }
            }
            candidate
        }
        val nextRuntime = nextId?.let { runtimes[it] }
        if (nextRuntime != null) {
            nextRuntime.state.value = DownloadState.DOWNLOADING
            launchTask(nextRuntime)
        }
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
        val maxRetries = settings.currentMaxTaskRetries().coerceAtLeast(0)
        var attempt = 0
        while (true) {
            try {
                coroutineScope {
                    chunks.map { chunk ->
                        // 在专用调度器上并发执行：每个分片一条阻塞请求，分片数即并发数（B2 功能②）。
                        async(downloadDispatcher) {
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
                return
            } catch (cancellation: CancellationException) {
                // C3：取消必须原样抛出，不能转成 FAILED
                throw cancellation
            } catch (exception: Exception) {
                attempt++
                if (attempt > maxRetries) {
                    Timber.e(
                        exception,
                        "DownloadEngine task %s failed after %d attempt(s)",
                        runtime.task.taskId,
                        attempt
                    )
                    runtime.state.value = DownloadState.FAILED
                    runtime.progress.value = runtime.progress.value.copy(
                        state = DownloadState.FAILED,
                        speedBytesPerSecond = DownloadProgressState.UNKNOWN_SIZE
                    )
                    return
                }
                Timber.e(
                    exception,
                    "DownloadEngine task %s retry %d/%d",
                    runtime.task.taskId,
                    attempt,
                    maxRetries
                )
                // 重试期间维持下载态，不对外发布 FAILED；已落盘分片作为断点续传起点。
                runtime.state.value = DownloadState.DOWNLOADING
                delay(TASK_RETRY_DELAY_MILLIS)
            }
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
                        // C1：按全局限速申请配额；不限速时立即返回。
                        speedLimiter.acquire(read, settings.currentSpeedLimitBytesPerSecond())
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
        /** 分片下载线程名前缀（便于抓日志 / 排查）。 */
        const val DOWNLOAD_THREAD_NAME = "jyd-download"

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

        /** 任务级失败重试的等待时长（C1）。 */
        const val TASK_RETRY_DELAY_MILLIS = 2000L

        /**
         * 「活动态」集合（C1）：处于这些状态的任务视为已在进行，重复 start 会被忽略。
         *
         * 注意：不含 [DownloadState.PAUSED]，否则 [resume] 内部调用 [start] 会被误判为重复启动。
         */
        val ACTIVE_STATES = setOf(DownloadState.PENDING, DownloadState.DOWNLOADING)
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

/**
 * 下载设置端口（C1）。
 *
 * 端口定义置于本文件内（不新增文件），由 data 层的 `AppSettingsStore` 实现，
 * 使 domain 层无需反向依赖 data 层即可读取「运行时下载设置」。
 *
 * 与进度端口一致，采用「读方法」而非直接传 StateFlow，避免 domain 层持有 data 层持久化细节。
 */
interface DownloadSettingsPort {

    /**
     * 当前最大同时下载任务数（C1）。
     *
     * @return 取值 1..5。
     */
    fun currentMaxConcurrentTasks(): Int

    /**
     * 当前下载限速（C1）。
     *
     * @return 单位字节/秒；0 表示不限速。
     */
    fun currentSpeedLimitBytesPerSecond(): Long

    /**
     * 当前任务级失败自动重试次数（C1）。
     *
     * @return 取值 0..5。
     */
    fun currentMaxTaskRetries(): Int
}

/**
 * 全局下载限速器（C1：令牌桶 / 时间预约模型）。
 *
 * 所有任务的所有分片共享同一实例，从而保证「限速」作用于整个 App 的总出口，
 * 而非逐分片限速。策略：每申请 [acquire] 的字节数，按当前限速换算为应占用的时长，
 * 预约到 [nextFreeNanos] 之后；若预约时间在未来则挂起等待，实现平滑限速。
 *
 * 线程安全：由 [mutex] 串行化预约计算，临界区极短；等待在锁外进行，不阻塞其他分片。
 */
private class SpeedLimiter {

    /** 预约计算锁。 */
    private val mutex = Mutex()

    /** 下一个可用时间点（单调时钟，纳秒）。 */
    private var nextFreeNanos: Long = 0L

    /**
     * 申请发送 [bytes] 字节的配额；超过限速时挂起到允许发送为止。
     *
     * @param bytes 本轮实际写入的字节数。
     * @param limitBytesPerSecond 当前限速，单位字节/秒；非正数表示不限速。
     */
    suspend fun acquire(bytes: Int, limitBytesPerSecond: Long) {
        if (limitBytesPerSecond <= 0L || bytes <= 0) {
            return
        }
        val waitNanos = mutex.withLock {
            val now = System.nanoTime()
            val grantedAt = if (nextFreeNanos < now) now else nextFreeNanos
            val costNanos = bytes.toLong() * NANOS_PER_SECOND / limitBytesPerSecond
            nextFreeNanos = grantedAt + costNanos
            grantedAt - now
        }
        if (waitNanos > 0L) {
            delay(waitNanos / NANOS_PER_MILLI)
        }
    }

    private companion object {
        /** 每秒纳秒数。 */
        const val NANOS_PER_SECOND = 1_000_000_000L

        /** 每毫秒纳秒数。 */
        const val NANOS_PER_MILLI = 1_000_000L
    }
}