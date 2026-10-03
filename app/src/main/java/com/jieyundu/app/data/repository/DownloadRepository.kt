// 文件：DownloadRepository.kt
// 职责：下载进度的仓库层，向上屏蔽 Room DAO 细节
// 依赖：DownloadDao、DownloadProgressState、DownloadEntity
// 协议：AGPL-3.0

package com.jieyundu.app.data.repository

import com.jieyundu.app.data.local.DownloadDao
import com.jieyundu.app.data.local.DownloadEntity
import com.jieyundu.app.domain.downloader.DownloadProgressState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 下载进度仓库。
 *
 * @param downloadDao 下载进度 DAO。
 */
@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao
) {

    /**
     * 写入或更新进度。
     *
     * @param progress 进度快照。
     */
    suspend fun upsertProgress(progress: DownloadProgressState) = downloadDao.upsert(progress)

    /**
     * 查询进度。
     *
     * @param taskId 任务 ID。
     * @return 进度快照；无记录时返回 null。
     */
    suspend fun queryProgress(taskId: String): DownloadProgressState? = downloadDao.query(taskId)

    /**
     * 删除进度。
     *
     * @param taskId 任务 ID。
     */
    suspend fun deleteProgress(taskId: String) = downloadDao.delete(taskId)

    /**
     * 观察全部下载进度。
     *
     * @return 进度快照列表流。
     */
    fun observeProgress(): Flow<List<DownloadProgressState>> =
        downloadDao.observeEntities().map { entities -> entities.map(DownloadEntity::toProgress) }
}
