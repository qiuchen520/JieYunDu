// 文件：HistoryRepository.kt
// 职责：下载历史的仓库层
// 依赖：HistoryDao、HistoryEntity
// 协议：AGPL-3.0

package com.jieyundu.app.data.repository

import com.jieyundu.app.data.local.HistoryDao
import com.jieyundu.app.data.local.HistoryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 下载历史仓库。
 *
 * @param historyDao 历史 DAO。
 */
@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao
) {

    /**
     * 记录一条下载历史。
     *
     * @param entity 历史实体。
     */
    suspend fun record(entity: HistoryEntity) = historyDao.insert(entity)

    /**
     * 观察全部历史。
     *
     * @return 历史列表流。
     */
    fun observeHistory(): Flow<List<HistoryEntity>> = historyDao.observeAll()

    /**
     * 按关键字搜索历史。
     *
     * @param keyword 关键字。
     * @return 匹配的历史记录。
     */
    suspend fun search(keyword: String): List<HistoryEntity> = historyDao.search(keyword)

    /**
     * 删除一条历史。
     *
     * @param id 记录 ID。
     */
    suspend fun delete(id: Long) = historyDao.deleteById(id)

    /**
     * 清空历史。
     */
    suspend fun clear() = historyDao.clear()
}
