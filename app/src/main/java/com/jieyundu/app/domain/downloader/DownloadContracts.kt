// 文件：DownloadContracts.kt
// 职责：下载引擎对外契约——进度 / 存档 / 设置三个端口 + 任务存档与续传结果类型
// 依赖：无（纯接口与值类型）
// 协议：AGPL-3.0

package com.jieyundu.app.domain.downloader

/**
 * 【修订 JYD-DEBT2-2026-10-07】债批次 2：本文件由 `DownloadEngine.kt` 拆出。
 *
 * 存在理由：`DownloadEngine.kt` 原本同时容纳「引擎实现」与「对外契约」（3 个端口 +
 * 2 个值类型），约 1000 行。引擎类本身是内聚的（调度 / 分片 / 续传 / 进度），
 * 但端口与值类型是给 data 层与 UI 层用的**契约面**，与实现混在一处会让
 * 「谁依赖谁」看不出层次。拆出后：引擎文件只剩实现，契约文件只有声明。
 *
 * 拆分原则（工程约束）：
 * - **同包搬迁**，包名不变 → 所有调用方的 import 与签名零改动、零行为变化；
 * - 端口由 data 层实现（Room `DownloadDao` / `AppSettingsStore`），domain 层不反向依赖 data；
 * - 说明：拆分本身不改动《要求.md》7.5 的任何方法签名，仅改动端口所在文件。
 */

/**
 * 下载任务存档（【JYD-P1-2026-10-04】P1-1 / P1-2）。
 *
 * 存在理由：进程被杀后内存中的运行态任务全部消失，重启后既要显示任务名（P1-1），
 * 也要能据直链 / 请求头 / 落盘路径重建任务继续下载（P1-2）。因此把「运行一次下载所需的
 * 输入参数」持久化下来，其字段与 [DownloadTask] 一一对应。
 *
 * @property taskId 任务 ID。
 * @property url 下载直链（有时效；过期后由上层重新解析）。
 * @property fileName 任务名（文件名）。
 * @property fileSize 文件总大小；未知时为 -1。
 * @property savePath 目标文件绝对路径。
 * @property chunkCount 分片数量。
 * @property headers 额外请求头。
 */
data class DownloadTaskRecord(
    val taskId: String,
    val url: String,
    val fileName: String,
    val fileSize: Long,
    val savePath: String,
    val chunkCount: Int,
    val headers: Map<String, String>
) {

    /**
     * 还原为运行态任务。
     *
     * @return 可直接交给 [DownloadEngine.start] 的任务描述。
     */
    fun toDownloadTask(): DownloadTask = DownloadTask(
        taskId = taskId,
        url = url,
        fileName = fileName,
        fileSize = fileSize,
        savePath = savePath,
        chunkCount = chunkCount,
        headers = headers
    )
}

/**
 * 下载任务的续传 / 重下入口的返回结果（【JYD-P1-2026-10-04】P1-2）。
 *
 * 存在理由（Owner 反馈「点继续 / 重新下载没反应」）：入口必须有明确反馈，
 * 因此引擎不再返回 Unit，而是把「到底发生了什么」显式交给 UI 决定提示文案。
 * 用密封接口而非枚举：后续若出现新的失败原因，可继续扩展而不破坏调用方穷尽判断。
 */
sealed interface EngineActionResult {

    /** 已开始续传（断点续传，不重下已有部分）。 */
    data object Resumed : EngineActionResult

    /** 已清空分片并从零开始。 */
    data object Restarted : EngineActionResult

    /** 无需处理（例如任务已在下载中）。 */
    data object NoOp : EngineActionResult

    /** 没有可用的持久化存档（旧版本记录 / 记录已删）→ UI 提示重新下载。 */
    data object NoCheckpoint : EngineActionResult

    /** 存档存在但分片临时文件已不存在 → UI 提示「文件已损坏，请重新下载」。 */
    data object MissingPartFiles : EngineActionResult
}

/**
 * 下载任务存档端口（【JYD-P1-2026-10-04】P1-1 / P1-2）。
 *
 * 由 data 层的 Room `DownloadDao` 实现，使 domain 层在保持不反向依赖 data 层的前提下
 * 完成「任务名 + 直链 + 请求头」的持久化与恢复。
 */
interface DownloadCheckpointPort {

    /**
     * 写入或更新任务存档。
     *
     * @param record 任务存档。
     */
    suspend fun upsertTask(record: DownloadTaskRecord)

    /**
     * 读取任务存档。
     *
     * @param taskId 任务 ID。
     * @return 任务存档；无记录或缺少直链时返回 null。
     */
    suspend fun loadTask(taskId: String): DownloadTaskRecord?
}

/**
 * 下载进度落库端口（依据【修订 JYD-ERRATA-2026-10-03】修订一）。
 *
 * 由 Room 的 `DownloadDao` 实现，从而在保持 domain 层不反向依赖 data 层的前提下
 * 完成进度持久化。
 */
interface DownloadProgressPort {

    /**
     * 写入或更新一条下载进度。
     *
     * 说明（【JYD-P1-2026-10-04】P1-1）：本方法**只更新进度相关列**，不负责建行、
     * 也不得触碰任务名 / 直链 / 请求头等列——任务名与续传信息由 [DownloadCheckpointPort.upsertTask]
     * 写入。实现方若用整行覆盖语义（REPLACE / Upsert），会把任务名清空，属实现缺陷。
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
 * 由 data 层的 `AppSettingsStore` 实现，使 domain 层无需反向依赖 data 层即可读取
 * 「运行时下载设置」。与进度端口一致，采用「读方法」而非直接传 StateFlow，
 * 避免 domain 层持有 data 层持久化细节。
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
