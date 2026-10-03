// 文件：AppDatabase.kt
// 职责：Room 数据库入口，聚合下载进度/历史/收藏三张表
// 依赖：Room、DownloadEntity、HistoryEntity、FavoriteEntity、各 DAO
// 协议：AGPL-3.0

package com.jieyundu.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 应用数据库。
 *
 * 版本 1 初始包含三张表：下载进度、下载历史、收藏。
 */
@Database(
    entities = [DownloadEntity::class, HistoryEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * @return 下载进度 DAO。
     */
    abstract fun downloadDao(): DownloadDao

    /**
     * @return 下载历史 DAO。
     */
    abstract fun historyDao(): HistoryDao

    /**
     * @return 收藏 DAO。
     */
    abstract fun favoriteDao(): FavoriteDao
}
