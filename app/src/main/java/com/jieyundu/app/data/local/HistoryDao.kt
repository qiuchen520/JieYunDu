// 文件：HistoryDao.kt
// 职责：下载历史表的数据访问对象
// 依赖：Room、HistoryEntity
// 协议：AGPL-3.0

package com.jieyundu.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 下载历史 DAO。
 */
@Dao
interface HistoryDao {

    /**
     * 插入或覆盖一条历史记录。
     *
     * @param entity 历史实体。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity)

    /**
     * 观察全部历史记录（按下载时间倒序）。
     *
     * @return 实体列表流。
     */
    @Query("SELECT * FROM download_history ORDER BY downloaded_at DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    /**
     * 按文件名关键字搜索历史。
     *
     * @param keyword 关键字。
     * @return 匹配的记录。
     */
    @Query(
        "SELECT * FROM download_history WHERE file_name LIKE '%' || :keyword || '%' " +
            "ORDER BY downloaded_at DESC"
    )
    suspend fun search(keyword: String): List<HistoryEntity>

    /**
     * 按主键删除一条记录。
     *
     * @param id 记录 ID。
     */
    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 清空全部历史记录。
     */
    @Query("DELETE FROM download_history")
    suspend fun clear()
}
