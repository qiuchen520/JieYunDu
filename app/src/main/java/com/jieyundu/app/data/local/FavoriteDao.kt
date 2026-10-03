// 文件：FavoriteDao.kt
// 职责：收藏表的数据访问对象
// 依赖：Room、FavoriteEntity
// 协议：AGPL-3.0

package com.jieyundu.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 收藏 DAO。
 */
@Dao
interface FavoriteDao {

    /**
     * 插入或覆盖一条收藏记录。
     *
     * @param entity 收藏实体。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    /**
     * 观察全部收藏（按收藏时间倒序）。
     *
     * @return 实体列表流。
     */
    @Query("SELECT * FROM favorites ORDER BY added_at DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    /**
     * 统计指定 fid 的收藏数量。
     *
     * @param fid 文件 ID。
     * @return 记录数。
     */
    @Query("SELECT COUNT(*) FROM favorites WHERE fid = :fid")
    suspend fun countByFid(fid: String): Int

    /**
     * 按 fid 删除收藏。
     *
     * @param fid 文件 ID。
     */
    @Query("DELETE FROM favorites WHERE fid = :fid")
    suspend fun deleteByFid(fid: String)

    /**
     * 清空全部收藏。
     */
    @Query("DELETE FROM favorites")
    suspend fun clear()
}
