// 文件：AppDatabase.kt
// 职责：Room 数据库入口，聚合下载进度/历史/收藏三张表
// 依赖：Room、DownloadEntity、HistoryEntity、FavoriteEntity、各 DAO
// 协议：AGPL-3.0

package com.jieyundu.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用数据库。
 *
 * 版本 1 初始包含三张表：下载进度、下载历史、收藏。
 * 版本 2（【修订 JYD-SAVEPATH-2026-10-03】）为 `download_progress` 增加 `save_path` 列。
 */
@Database(
    entities = [DownloadEntity::class, HistoryEntity::class, FavoriteEntity::class],
    version = 2,
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

    companion object {

        /**
         * 版本 1 → 2 迁移：为 `download_progress` 增加 `save_path` 列。
         *
         * 说明：新增列可为空、无默认值约束，历史行该列取 NULL，
         * 因此老版本升级不会丢数据（D15）。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE download_progress ADD COLUMN save_path TEXT"
                )
            }
        }
    }
}