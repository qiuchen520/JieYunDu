// 文件：DownloadEngine.kt
// 职责：下载引擎门面——任务生命周期（启动/暂停/继续/重下/取消）、并发调度闸门、进度发布
// 依赖：ChunkManager、ChunkDownloader、DownloadTaskRuntime、SpeedLimiter、DownloadContracts、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.downloader

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
 * @param checkpoint 任务存档端口（【JYD-P1-2026-10-04】：任务名 / 直链 / 请求头持久化）。
 * @param settings 下载设置端口（并发 / 限速 / 重试）。
 */
@Singleton
class DownloadEngine @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val downloadDao: DownloadProgressPort,
    private val checkpoint: DownloadCheckpointPort,
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
    private val runtimes: MutableMap<String, DownloadTaskRuntime> = ConcurrentHashMap()

    /**
     * 全部任务的实时进度快照（内存态，**不落库**）。
     *
     * 存在理由（Owner 反馈「下载页速度恒为 `--`」）：下载页原先只订阅 Room 持久化进度，
     * 而 Room 仅在「开始 / 暂停 / 完成」三刻写入、且**不保存速度字段**，
     * 导致界面看不到实时数值。本表承载引擎运行期的实时进度（按
     * [PROGRESS_INTERVAL_MILLIS] 节流刷新），供 UI 层与 Room 数据合并展示；
     * 为性能考虑，**不**随每次刷新写库。
     */
    private val liveProgressFlow: MutableStateFlow<Map<String, DownloadProgressState>> =
        MutableStateFlow(emptyMap())

    /**
     * 实时进度快照的只读流（任务 ID → 进度）。
     *
     * 说明：UI 订阅本流即可获得下载中的实时速度 / 进度；任务取消后其条目会从此表移除。
     * 该流是 [observeProgress]（按任务订阅）之外的「全量视图」，二者都基于内存态，互不影响。
     */
    val liveProgress: StateFlow<Map<String, DownloadProgressState>> = liveProgressFlow.asStateFlow()

    /** 任务调度锁（C1：并发闸门；保护 [runningTaskCount] 与 [pendingQueue]）。 */
    private val scheduleMutex = Mutex()

    /** 等待执行的任务 ID 队列（FIFO，C1）。 */
    private val pendingQueue: ArrayDeque<String> = ArrayDeque()

    /** 当前正在执行的任务数（C1，受 [scheduleMutex] 保护）。 */
    private var runningTaskCount: Int = 0

    /** 全局下载限速器（C1：所有任务与其分片共享一个令牌桶）。 */
    private val speedLimiter = SpeedLimiter()

    /** 单分片下载器（【修订 JYD-DEBT3-2026-10-07】自本类拆出，同包）。 */
    private val chunkDownloader = ChunkDownloader(okHttpClient, speedLimiter, settings)

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

        val chunks = prepareRuntime(task)
        // 【JYD-SAVEPATH-2026-10-03】启动即落库：既让新任务立即出现。
        // 【JYD-P1-2026-10-04】改为**任务存档**写入（含任务名 / 直链 / 请求头）：
        // 任务名据此在重启后仍可显示（P1-1），直链与请求头据此在重启后仍可续传（P1-2）。
        checkpoint.upsertTask(
            DownloadTaskRecord(
                taskId = task.taskId,
                url = task.url,
                fileName = task.fileName,
                fileSize = task.fileSize,
                savePath = task.savePath,
                chunkCount = task.chunkCount,
                headers = task.headers
            )
        )
        // 进度落库：行已在上一句建好，这里只更新进度列（不会碰任务名等列）。
        downloadDao.upsert(runtimes.getValue(task.taskId).progress.value)
        // 【JYD-DLSPEED-2026-10-04】启动即写入内存实时快照，下载页据此展示实时数值。
        publishLive(runtimes.getValue(task.taskId).progress.value)
        // C1：不直接启动，交给调度器按「最大同时下载任务数」排队 / 放行。
        schedule(runtimes.getValue(task.taskId))
    }

    /**
     * 准备（或重置）任务的运行态：切分分片、读取已落盘进度、写入初始快照。
     *
     * 说明：本方法被 [start]（新任务 / 继续）与 [resumeFromRecord]（重启后继续）共用，
     * 确保「重启续传」与「正常续传」走同一套分片与断点计算逻辑，不会出现两套行为。
     *
     * @param task 运行态任务。
     * @return 本次构造的分片列表。
     */
    private fun prepareRuntime(task: DownloadTask): List<Chunk> {
        val targetFile = File(task.savePath)
        val requestedCount = task.chunkCount.coerceIn(
            DownloadTask.MIN_CHUNK_COUNT,
            DownloadTask.MAX_CHUNK_COUNT
        )
        val chunks = chunkManager.createChunks(task.fileSize, requestedCount).map { chunk ->
            chunk.withDownloaded(chunkManager.readPartProgress(targetFile, chunk.index))
        }

        val runtime = runtimes.getOrPut(task.taskId) { DownloadTaskRuntime(task, chunks.size) }
        runtime.chunks = chunks
        runtime.sessionBytes.set(0L)
        runtime.initialBytes = chunks.sumOf { chunk -> chunk.downloadedBytes }
        runtime.lastSpeedSampleAt = System.currentTimeMillis()
        runtime.lastSpeedSampleBytes = runtime.initialBytes
        runtime.sessionStartAt = System.currentTimeMillis()
        runtime.lastEmitAt = 0L
        runtime.state.value = DownloadState.DOWNLOADING
        runtime.progress.value = DownloadProgressState(
            taskId = task.taskId,
            state = DownloadState.DOWNLOADING,
            downloadedBytes = runtime.initialBytes,
            totalBytes = task.fileSize,
            speedBytesPerSecond = 0L,
            averageSpeedBytesPerSecond = 0L,
            chunkCount = chunks.size,
            completedChunks = chunks.count { chunk -> chunk.isCompleted },
            savePath = task.savePath,
            // 【JYD-P1B-2026-10-04】把任务名带进内存快照，供 UI 在会话登记表为空时使用。
            fileName = task.fileName
        )
        return chunks
    }

    /**
     * 任务调度闸门（C1）：按「最大同时下载任务数」决定立即执行还是排队。
     *
     * 若当前运行任务数未达上限则占用一个槽位并立即启动；否则入 [pendingQueue] 等待，
     * 状态置为 [DownloadState.PENDING]。任一任务结束时释放槽位并从队列头部补位。
     *
     * @param runtime 运行态任务。
     */
    private suspend fun schedule(runtime: DownloadTaskRuntime) {
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
    private fun launchTask(runtime: DownloadTaskRuntime) {
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
            speedBytesPerSecond = 0L,
            averageSpeedBytesPerSecond = 0L
        )
        downloadDao.upsert(runtime.progress.value)
        // 【JYD-DLSPEED-2026-10-04】暂停即发布最终态，界面立刻显示「已暂停 + 速度归零」。
        publishLive(runtime.progress.value)
        Timber.i("DownloadEngine paused task %s", taskId)
    }

    /**
     * 继续任务。
     *
     * @param taskId 任务 ID；对应的运行态任务不存在时无法恢复（错误日志提示）。
     */
    suspend fun resume(taskId: String): EngineActionResult {
        val runtime = runtimes[taskId]
        if (runtime == null) {
            // 【JYD-P1-2026-10-04】P1-2：进程重启后内存运行态已不存在，改由持久化存档重建。
            return resumeFromCheckpoint(taskId)
        }
        if (runtime.state.value == DownloadState.DOWNLOADING) {
            Timber.i("DownloadEngine resume ignored: task %s already downloading", taskId)
            return EngineActionResult.NoOp
        }
        val persisted = downloadDao.query(taskId)
        val downloaded = persisted?.downloadedBytes ?: runtime.initialBytes
        Timber.i(
            "DownloadEngine resume task=%s savePath=%s partExists=%s downloadedBytes=%d",
            taskId,
            runtime.task.savePath,
            hasPartFiles(runtime.task.savePath),
            downloaded
        )
        start(runtime.task)
        return EngineActionResult.Resumed
    }

    /**
     * 重启后按持久化存档续传（P1-2 主路径）。
     *
     * 判定顺序：
     * 1. 存档缺失或缺少直链（老数据）→ [EngineActionResult.NoCheckpoint]，UI 提示重新下载；
     * 2. 存档存在但 `.part` 分片全部不存在（被清理 / 被打断落盘）→
     *    [EngineActionResult.MissingPartFiles]，UI 提示「文件已损坏，请重新下载」；
     * 3. 分片存在 → 重建运行态并续传（Range 从断点起，不重下已有部分）。
     *
     * @param taskId 任务 ID。
     * @return 续传结果。
     */
    private suspend fun resumeFromCheckpoint(taskId: String): EngineActionResult {
        val record = checkpoint.loadTask(taskId)
        if (record == null) {
            Timber.w("DownloadEngine resume task=%s has no usable checkpoint", taskId)
            return EngineActionResult.NoCheckpoint
        }
        if (!hasPartFiles(record.savePath)) {
            Timber.w(
                "DownloadEngine resume task=%s savePath=%s partExists=false -> missing part files",
                taskId,
                record.savePath
            )
            return EngineActionResult.MissingPartFiles
        }
        val task = record.toDownloadTask()
        Timber.i(
            "DownloadEngine resume(restart-process) task=%s savePath=%s partExists=true fileSize=%d",
            taskId,
            task.savePath,
            task.fileSize
        )
        start(task)
        return EngineActionResult.Resumed
    }

    /**
     * 重新下载（P1-2）：清空该任务的 `.part` 分片后从零开始。
     *
     * 说明：优先按持久化存档重建任务（重启后可用）；若调用方已有运行态则直接用其任务描述。
     * 分片被清空后 [prepareRuntime] 读到的已落盘长度全为 0，即从零开始。
     *
     * @param taskId 任务 ID。
     * @return 重下结果。
     */
    suspend fun restart(taskId: String): EngineActionResult {
        val runtime = runtimes[taskId]
        val task = when {
            runtime != null -> runtime.task
            else -> checkpoint.loadTask(taskId)?.toDownloadTask()
        }
        if (task == null) {
            Timber.w("DownloadEngine restart task=%s has no usable checkpoint", taskId)
            return EngineActionResult.NoCheckpoint
        }
        runtime?.job?.cancel()
        runtime?.job = null
        val targetFile = File(task.savePath)
        val removed = chunkManager.deleteAllPartFiles(targetFile)
        runtime?.chunks = emptyList()
        Timber.i(
            "DownloadEngine restart task=%s savePath=%s removedParts=%d",
            taskId,
            task.savePath,
            removed
        )
        start(task)
        return EngineActionResult.Restarted
    }

    /**
     * 判断目标文件是否还存在分片临时文件（P1-2：续传可行性判定）。
     *
     * @param savePath 目标文件绝对路径。
     * @return true 表示至少存在一个 `.part` 分片。
     */
    private fun hasPartFiles(savePath: String?): Boolean {
        val path = savePath?.takeIf { value -> value.isNotBlank() } ?: return false
        return chunkManager.hasPartFiles(File(path))
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
            speedBytesPerSecond = DownloadProgressState.UNKNOWN_SIZE,
            averageSpeedBytesPerSecond = DownloadProgressState.UNKNOWN_SIZE
        )
        // 【JYD-DLSPEED-2026-10-04】任务已销毁：实时表中移除条目，避免残留脏数据。
        removeLive(taskId)
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
     * 把一份进度快照写入内存实时表（【JYD-DLSPEED-2026-10-04】）。
     *
     * 说明：
     * - 本方法是「下载页速度恒 `--`」修复的写入口，**只改内存、不写库**（Owner 要求③：
     *   不为显示速度频繁写 Room）；落库仍只发生在 start / pause / complete 三刻；
     * - 多分片协程会并发调用，故用「读当前值 + 生成新 Map + CAS 回写」而非 `+=`，
     *   避免并发下丢更新；
     * - 调用方须自行保证节流（[publishProgress] 已按 [PROGRESS_INTERVAL_MILLIS] 节流）。
     *
     * @param progress 最新进度快照（含任务 ID）。
     */
    private fun publishLive(progress: DownloadProgressState) {
        liveProgressFlow.update { current -> current + (progress.taskId to progress) }
    }

    /**
     * 从内存实时表中移除某任务的条目（任务取消后调用）。
     *
     * @param taskId 任务 ID。
     */
    private fun removeLive(taskId: String) {
        liveProgressFlow.update { current -> current - taskId }
    }

    /**
     * 执行任务主体：并发下载全部分片 → 合并 → 落库。
     *
     * @param runtime 运行态任务。
     */
    private suspend fun runTask(runtime: DownloadTaskRuntime) {
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
                            chunkDownloader.download(
                                runtime = runtime,
                                chunk = chunk,
                                partFile = chunkManager.partFile(targetFile, chunk.index)
                            ) { sessionTotal -> publishProgress(runtime, sessionTotal) }
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
                    averageSpeedBytesPerSecond = 0L,
                    completedChunks = chunks.size
                )
                downloadDao.upsert(runtime.progress.value)
                // 【JYD-DLSPEED-2026-10-04】完成即发布最终态（速度归零、进度满格）。
                publishLive(runtime.progress.value)
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
                    // 【JYD-DLSPEED-2026-10-04】失败即发布最终态，界面不再残留旧速度。
                    publishLive(runtime.progress.value)
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
     * 以节流方式刷新进度快照并计算瞬时速度。
     *
     * @param runtime 运行态任务。
     * @param sessionBytes 本次运行累计写入的字节数。
     */
    private fun publishProgress(runtime: DownloadTaskRuntime, sessionBytes: Long) {
        // 【JYD-DLSPEED2-2026-10-04】仅活动态才刷新：pause() 取消协程到真正停下的短暂窗口内，
        // 在途分片可能仍调用本方法；若此时发布 DOWNLOADING 快照，会把刚写入的「已暂停」
        // 覆盖回去，表现为「点了暂停又跳回下载中」。
        if (runtime.state.value !in ACTIVE_STATES) {
            return
        }
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

        // 平均速度（Owner 反馈）：本运行累计写入字节 ÷ 本运行已进行的时长，反映整体吞吐。
        val sessionElapsed = now - runtime.sessionStartAt
        val averageSpeed = if (sessionElapsed > 0L) {
            sessionBytes * 1000L / sessionElapsed
        } else {
            DownloadProgressState.UNKNOWN_SIZE
        }

        runtime.progress.value = runtime.progress.value.copy(
            state = runtime.state.value,
            downloadedBytes = downloaded,
            totalBytes = if (runtime.task.hasKnownSize) {
                runtime.task.fileSize
            } else {
                DownloadProgressState.UNKNOWN_SIZE
            },
            speedBytesPerSecond = speed,
            averageSpeedBytesPerSecond = averageSpeed
        )
        // 【JYD-DLSPEED-2026-10-04】实时速度的来源：节流后的每次刷新同步到内存实时表，
        // 供下载页订阅展示；此处**不落库**（Owner 要求③：不为显示速度频繁写 Room）。
        publishLive(runtime.progress.value)
    }


    /**
     * 伴生对象（**public**，供下载页复用状态判定口径）。
     *
     * 说明：内部常量以 `private` 成员保留在本伴生对象内（不外泄）；对外仅暴露
     * [ACTIVE_STATES]，「活动态」判定因此只有一处定义，避免 UI 层复制集合导致漂移。
     */
    companion object {
        /** 分片下载线程名前缀（便于抓日志 / 排查）。 */
        private const val DOWNLOAD_THREAD_NAME = "jyd-download"

        /** 进度发射节流间隔。 */
        private const val PROGRESS_INTERVAL_MILLIS = 200L

        /** 任务级失败重试的等待时长（C1）。 */
        private const val TASK_RETRY_DELAY_MILLIS = 2000L

        /**
         * 「活动态」集合（C1）：处于这些状态的任务视为已在进行，重复 start 会被忽略。
         *
         * 注意：不含 [DownloadState.PAUSED]，否则 [resume] 内部调用 [start] 会被误判为重复启动。
         *
         * 对外可见（【JYD-DLSPEED-2026-10-04】）：下载页在合并实时进度时需按同一口径判定
         * 任务是否仍在进行，故把判定集合公开为只读值，避免 UI 层另建一份集合产生漂移。
         */
        val ACTIVE_STATES: Set<DownloadState> = setOf(DownloadState.PENDING, DownloadState.DOWNLOADING)
    }
}

// 【修订 JYD-DEBT2-2026-10-07】端口（进度 / 存档 / 设置）与契约值类型
// （DownloadTaskRecord / EngineActionResult）已拆至同包 DownloadContracts.kt。
// 【修订 JYD-DEBT3-2026-10-07】本文件只保留引擎门面与调度：内部类 SpeedLimiter →
// DownloadSpeedLimiter.kt、TaskRuntime → DownloadTaskRuntime.kt、单分片下载 →
// ChunkDownloader.kt（均同包，调用方无感）。

