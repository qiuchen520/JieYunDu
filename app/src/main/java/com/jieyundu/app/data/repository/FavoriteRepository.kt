// 文件：FavoriteRepository.kt
// 职责：收藏项的仓库层
// 依赖：FavoriteDao、FavoriteEntity
// 协议：AGPL-3.0

package com.jieyundu.app.data.repository

import com.jieyundu.app.data.local.FavoriteDao
import com.jieyundu.app.data.local.FavoriteEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 收藏仓库。
 *
 * @param favoriteDao 收藏 DAO。
 */
@Singleton
class FavoriteRepository @Inject constructor(
    private val favoriteDao: FavoriteDao
) {

    /**
     * 新增一条收藏。
     *
     * @param entity 收藏实体。
     */
    suspend fun add(entity: FavoriteEntity) = favoriteDao.insert(entity)

    /**
     * 观察全部收藏。
     *
     * @return 收藏列表流。
     */
    fun observeFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.observeAll()

    /**
     * 判断指定文件是否已收藏。
     *
     * @param fid 文件 ID。
     * @return true 表示已收藏。
     */
    suspend fun isFavorite(fid: String): Boolean = favoriteDao.countByFid(fid) > 0

    /**
     * 取消收藏。
     *
     * @param fid 文件 ID。
     */
    suspend fun remove(fid: String) = favoriteDao.deleteByFid(fid)

    /**
     * 清空收藏。
     */
    suspend fun clear() = favoriteDao.clear()
}
