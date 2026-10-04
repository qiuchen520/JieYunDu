// 文件：DownloadDao.kt
// 职责：下载进度表的数据访问对象，实现 domain 的 DownloadProgressPort
// 依赖：Room、DownloadEntity、DownloadProgressPort、DownloadTaskRecord、DownloadCheckpointPort
// 协议：AGPL-3.0

package com.jieyundu.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jieyundu.app.domain.downloader.DownloadCheckpointPort
import com.jieyundu.app.domain.downloader.DownloadProgressPort
import com.jieyundu.app.domain.downloader.DownloadProgressState
import com.jieyundu.app.domain.downloader.DownloadState
import com.jieyundu.app.domain.downloader.DownloadTaskRecord
import kotlinx.coroutines.flow.Flow

/**
 * 下载进度 DAO。
 *
 * 依据【修订 JYD-ERRATA-2026-10-03】修订一，本 DAO 直接实现 [DownloadProgressPort]，
 * 使 domain 层的 DownloadEngine 无需依赖 data 层类型即可完成进度落库；
 * 自【JYD-P1-2026-10-04】起同时实现 [DownloadCheckpointPort]（任务存档：任务名 / 直链 /
 * 请求头 / 转存副本关联），支撑重启后继续下载。
 *
 * **关键实现约束（P1-1 防回退）**：进度刷新频率达每 200ms 一次。若用 `REPLACE` 写入，
 * 未提供的列会被覆盖成默认值 → 任务名与直链会被进度刷新清空；若用 `@Upsert`，任务名列
 * 因为“本次未提供”同样会被写回默认值。因此本 DAO 采用两条**显式**写路径：
 * - [upsertTaskEntity]（任务存档，含任务名 / 直链 / 请求头）：按主键存在与否决定 UPDATE 或 INSERT；
 * - [updateProgressColumns]（每 200ms 的进度刷新）：**只更新进度列**，任务名等列一律不碰。
 */
@Dao
abstract class DownloadDao : DownloadProgressPort, DownloadCheckpointPort {

    /**
     * 插入一条**新**任务的完整行（含任务名 / 直链 / 请求头）。
     *
     * 说明：是否「新」由 [existsTask] 显式判定后决定调用，**不依赖** `@Insert` 的返回语义
     * （Room 在部分版本上把单主键插入的返回值生成为 `Unit`，靠返回值判断会编译失败且不稳）。
     *
     * @param entity 完整任务实体。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertEntity(entity: DownloadEntity)

    /**
     * 判断指定任务是否已存在（用于决定「插入」还是「按列更新」）。
     *
     * @param taskId 任务 ID。
     * @return 1 表示已存在，0 表示不存在。
     */
    @Query("SELECT COUNT(*) FROM download_progress WHERE task_id = :taskId")
    abstract suspend fun existsTask(taskId: String): Int

    /**
     * 仅更新进度相关列（不触碰任务名 / 直链 / 请求头等列）。
     *
     * @param taskId 任务 ID。
     * @param downloadedBytes 已下载字节数。
     * @param totalBytes 总字节数。
     * @param state 状态名。
     * @param completedChunks 已完成分片数。
     * @param chunkCount 分片总数。
     * @param updatedAt 更新时间戳。
     */
    @Query(
        "UPDATE download_progress SET downloaded_bytes = :downloadedBytes, " +
            "total_bytes = :totalBytes, state = :state, " +
            "completed_chunks = :completedChunks, chunk_count = :chunkCount, " +
            "updated_at = :updatedAt WHERE task_id = :taskId"
    )
    abstract suspend fun updateProgressColumns(
        taskId: String,
        downloadedBytes: Long,
        totalBytes: Long,
        state: String,
        completedChunks: Int,
        chunkCount: Int,
        updatedAt: Long
    )

    /**
     * 写入任务存档（含任务名 / 直链 / 请求头）：主键不存在则插入，存在则按列更新。
     *
     * @param entity 任务存档实体。
     */
    suspend fun upsertTaskEntity(entity: DownloadEntity) {
        if (existsTask(entity.taskId) == 0) {
            insertEntity(entity)
            return
        }
        updateTaskColumns(
            taskId = entity.taskId,
            fileName = entity.fileName,
            url = entity.url,
            headers = entity.headers,
            savePath = entity.savePath,
            totalBytes = entity.totalBytes,
            chunkCount = entity.chunkCount,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * 更新任务存档列（任务名 / 直链 / 请求头 / 落盘路径 / 总量 / 分片数）。
     *
     * @param taskId 任务 ID。
     * @param fileName 任务名。
     * @param url 直链。
     * @param headers 请求头持久化字符串。
     * @param savePath 落盘路径。
     * @param totalBytes 总字节数。
     * @param chunkCount 分片总数。
     * @param updatedAt 更新时间戳。
     */
    @Query(
        "UPDATE download_progress SET file_name = :fileName, url = :url, headers = :headers, " +
            "save_path = :savePath, total_bytes = :totalBytes, chunk_count = :chunkCount, " +
            "updated_at = :updatedAt WHERE task_id = :taskId"
    )
    abstract suspend fun updateTaskColumns(
        taskId: String,
        fileName: String,
        url: String?,
        headers: String?,
        savePath: String?,
        totalBytes: Long,
        chunkCount: Int,
        updatedAt: Long
    )

    /**
     * 按任务 ID 查询进度实体。
     *
     * @param taskId 任务 ID。
     * @return 实体；无记录时返回 null。
     */
    @Query("SELECT * FROM download_progress WHERE task_id = :taskId LIMIT 1")
    abstract suspend fun queryEntity(taskId: String): DownloadEntity?

    /**
     * 观察全部进度记录（按更新时间倒序）。
     *
     * @return 实体列表流。
     */
    @Query("SELECT * FROM download_progress ORDER BY updated_at DESC")
    abstract fun observeEntities(): Flow<List<DownloadEntity>>

    /**
     * 把处于「活动态」（等待 / 下载中）的任务统一标记为已暂停（P1-2：重启后状态纠正）。
     *
     * 背景：进程被杀后内存中的运行态消失，但库里仍留着 `PENDING` / `DOWNLOADING`；
     * 若不纠正，界面会显示「下载中」却永远不动，与「点了没反应」的观感完全一致。
     *
     * @param fromStates 需要纠正的原状态名列表。
     * @param toState 目标状态名（已暂停）。
     * @param updatedAt 更新时间戳。
     * @return 被纠正的行数。
     */
    @Query(
        "UPDATE download_progress SET state = :toState, updated_at = :updatedAt " +
            "WHERE state IN (:fromStates)"
    )
    abstract suspend fun markActiveAsPaused(
        fromStates: List<String>,
        toState: String,
        updatedAt: Long
    ): Int

    /**
     * 删除指定任务的进度记录（实现 [DownloadProgressPort.delete]）。
     *
     * @param taskId 任务 ID。
     */
    @Query("DELETE FROM download_progress WHERE task_id = :taskId")
    abstract override suspend fun delete(taskId: String)

    /**
     * 写入或更新进度（实现 [DownloadProgressPort.upsert]）。
     *
     * 说明（P1-1 防回退）：本方法**只写进度列**，且对不存在的任务不建行——
     * 任务名 / 直链 / 请求头由 [upsertTask] 一次性写入，此后每 200ms 的进度刷新
     * 不会再触碰它们，从根本上杜绝「进度刷新把任务名清成空」的回退。
     *
     * @param progress 进度快照。
     */
    override suspend fun upsert(progress: DownloadProgressState) {
        updateProgressColumns(
            taskId = progress.taskId,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes,
            state = progress.state.name,
            completedChunks = progress.completedChunks,
            chunkCount = progress.chunkCount,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 查询进度（实现 [DownloadProgressPort.query]）。
     *
     * @param taskId 任务 ID。
     * @return 进度快照；无记录时返回 null。
     */
    override suspend fun query(taskId: String): DownloadProgressState? =
        queryEntity(taskId)?.toProgress()

    /**
     * 写入或更新任务存档（实现 [DownloadCheckpointPort.upsertTask]）。
     *
     * @param record 任务存档。
     */
    override suspend fun upsertTask(record: DownloadTaskRecord) {
        val existing = queryEntity(record.taskId)
        val entity = DownloadEntity.fromTaskRecord(
            record = record,
            state = existing?.state ?: DownloadState.PENDING.name,
            downloadedBytes = existing?.downloadedBytes ?: 0L
        )
        upsertTaskEntity(entity)
    }

    /**
     * 读取任务存档（实现 [DownloadCheckpointPort.loadTask]）。
     *
     * @param taskId 任务 ID。
     * @return 任务存档；无记录或缺少直链时返回 null。
     */
    override suspend fun loadTask(taskId: String): DownloadTaskRecord? =
        queryEntity(taskId)?.toTaskRecord()
}
