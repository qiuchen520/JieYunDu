// 文件：DownloadDao.kt
// 职责：下载进度表的数据访问对象，实现 domain 的 DownloadProgressPort
// 依赖：Room、DownloadEntity、DownloadProgressPort、DownloadProgressState
// 协议：AGPL-3.0

package com.jieyundu.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jieyundu.app.domain.downloader.DownloadProgressPort
import com.jieyundu.app.domain.downloader.DownloadProgressState
import kotlinx.coroutines.flow.Flow

/**
 * 下载进度 DAO。
 *
 * 依据【修订 JYD-ERRATA-2026-10-03】修订一，本 DAO 直接实现 [DownloadProgressPort]，
 * 使 domain 层的 DownloadEngine 无需依赖 data 层类型即可完成进度落库。
 *
 * 实现方式：端口方法 [upsert] / [query] 在抽象类中以具体方法实现，
 * 由 Room 生成的带注解方法支撑；[delete] 的参数与实体列一致，直接由 Room 生成。
 */
@Dao
abstract class DownloadDao : DownloadProgressPort {

    /**
     * 插入或覆盖一条进度实体。
     *
     * @param entity 进度实体。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertEntity(entity: DownloadEntity)

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
     * 删除指定任务的进度记录（实现 [DownloadProgressPort.delete]）。
     *
     * @param taskId 任务 ID。
     */
    @Query("DELETE FROM download_progress WHERE task_id = :taskId")
    abstract override suspend fun delete(taskId: String)

    /**
     * 写入或更新进度（实现 [DownloadProgressPort.upsert]）。
     *
     * @param progress 进度快照。
     */
    override suspend fun upsert(progress: DownloadProgressState) {
        upsertEntity(DownloadEntity.fromProgress(progress))
    }

    /**
     * 查询进度（实现 [DownloadProgressPort.query]）。
     *
     * @param taskId 任务 ID。
     * @return 进度快照；无记录时返回 null。
     */
    override suspend fun query(taskId: String): DownloadProgressState? =
        queryEntity(taskId)?.toProgress()
}
