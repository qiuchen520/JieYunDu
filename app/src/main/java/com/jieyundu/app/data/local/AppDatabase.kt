// 文件：AppDatabase.kt
// 职责：Room 数据库入口，聚合下载进度/历史/收藏/转存登记四张表
// 依赖：Room、DownloadEntity、HistoryEntity、FavoriteEntity、TransferRecordEntity、各 DAO
// 协议：AGPL-3.0

package com.jieyundu.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用数据库。
 *
 * 版本历史：
 * - 版本 1：下载进度、下载历史、收藏三张表；
 * - 版本 2（【修订 JYD-SAVEPATH-2026-10-03】）：`download_progress` 增加 `save_path` 列；
 * - 版本 3（【JYD-P1-2026-10-04】）：`download_progress` 增加 `file_name` / `url` / `headers` 三列
 *   （任务名 + 续传所需的直链与请求头），并新增 `transfer_records` 表
 *   （转存副本登记，重启后仍可识别与清理）。
 */
@Database(
    entities = [
        DownloadEntity::class,
        HistoryEntity::class,
        FavoriteEntity::class,
        TransferRecordEntity::class
    ],
    version = 3,
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

    /**
     * @return 转存副本登记 DAO（【JYD-P1-2026-10-04】）。
     */
    abstract fun transferRecordDao(): TransferRecordDao

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

        /**
         * 版本 2 → 3 迁移（【JYD-P1-2026-10-04】P1-1 / P1-2 / P1-3）。
         *
         * 变更内容：
         * 1. `download_progress` 增加四列，均带默认值，**老数据不崩**：
         *    - `file_name`：任务名（P1-1；老数据回退空串 → UI 显示「未命名任务」）；
         *    - `url`：直链（P1-2；老数据无值 → 重启续传时提示重新下载）；
         *    - `headers`：请求头（每行 `name: value`，P1-2；同上）；
         * 2. 新增 `transfer_records` 表（P1-3）：把「哪个文件是本 App 转存出来的」事实落库，
         *    使删除安全守卫在重启后仍能识别临时副本；
         * 3. 新增 `index_transfer_records_dir_fid` 索引（与实体注解一致，否则 Room 校验不通过）。
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE download_progress ADD COLUMN file_name TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE download_progress ADD COLUMN url TEXT"
                )
                database.execSQL(
                    "ALTER TABLE download_progress ADD COLUMN headers TEXT"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS transfer_records (" +
                        "fid TEXT NOT NULL PRIMARY KEY, " +
                        "file_name TEXT, " +
                        "dir_fid TEXT, " +
                        "netdisk_type TEXT, " +
                        "created_at INTEGER NOT NULL)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transfer_records_dir_fid " +
                        "ON transfer_records (dir_fid)"
                )
            }
        }
    }
}
