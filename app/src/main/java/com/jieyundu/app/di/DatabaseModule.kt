// 文件：DatabaseModule.kt
// 职责：提供 Room 数据库、三个 DAO 与下载进度落库端口
// 依赖：Room、AppDatabase、DownloadDao、HistoryDao、FavoriteDao、DownloadProgressPort、Hilt
// 协议：AGPL-3.0

package com.jieyundu.app.di

import android.content.Context
import androidx.room.Room
import com.jieyundu.app.data.local.AppDatabase
import com.jieyundu.app.data.local.DownloadDao
import com.jieyundu.app.data.local.FavoriteDao
import com.jieyundu.app.data.local.HistoryDao
import com.jieyundu.app.data.local.TransferRecordDao
import com.jieyundu.app.domain.downloader.DownloadCheckpointPort
import com.jieyundu.app.domain.downloader.DownloadProgressPort
import com.jieyundu.app.domain.transfer.TransferRecordPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据层依赖提供者（阶段 5 交付物）。
 *
 * 依据【修订 JYD-ERRATA-2026-10-03】修订一，[DownloadDao] 实现 [DownloadProgressPort]，
 * 本模块把该实现绑定到端口类型，供 DownloadEngine 注入。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** 数据库文件名。 */
    private const val DATABASE_NAME = "jieyundu.db"

    /**
     * 提供 Room 数据库实例。
     *
     * @param context 应用上下文。
     * @return 数据库实例。
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    /**
     * 提供下载进度 DAO。
     *
     * @param database 数据库实例。
     * @return 下载进度 DAO。
     */
    @Provides
    fun provideDownloadDao(database: AppDatabase): DownloadDao = database.downloadDao()

    /**
     * 提供历史 DAO。
     *
     * @param database 数据库实例。
     * @return 历史 DAO。
     */
    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao = database.historyDao()

    /**
     * 提供收藏 DAO。
     *
     * @param database 数据库实例。
     * @return 收藏 DAO。
     */
    @Provides
    fun provideFavoriteDao(database: AppDatabase): FavoriteDao = database.favoriteDao()

    /**
     * 提供转存登记 DAO（【JYD-P1-2026-10-04】P1-3）。
     *
     * @param database 数据库实例。
     * @return 转存登记 DAO。
     */
    @Provides
    fun provideTransferRecordDao(database: AppDatabase): TransferRecordDao =
        database.transferRecordDao()

    /**
     * 把 Room 的 [DownloadDao] 绑定为 [DownloadProgressPort]。
     *
     * @param downloadDao 下载进度 DAO。
     * @return 端口实现。
     */
    @Provides
    @Singleton
    fun provideDownloadProgressPort(downloadDao: DownloadDao): DownloadProgressPort = downloadDao

    /**
     * 把 Room 的 [DownloadDao] 绑定为 [DownloadCheckpointPort]（【JYD-P1-2026-10-04】P1-1 / P1-2）。
     *
     * 说明：任务名与续传所需的直链 / 请求头由该端口持久化，使引擎在进程重启后仍能重建任务。
     *
     * @param downloadDao 下载进度 DAO。
     * @return 端口实现。
     */
    @Provides
    @Singleton
    fun provideDownloadCheckpointPort(downloadDao: DownloadDao): DownloadCheckpointPort = downloadDao

    /**
     * 把 Room 的 [TransferRecordDao] 绑定为 [TransferRecordPort]（【JYD-P1-2026-10-04】P1-3）。
     *
     * @param transferRecordDao 转存登记 DAO。
     * @return 端口实现。
     */
    @Provides
    @Singleton
    fun provideTransferRecordPort(transferRecordDao: TransferRecordDao): TransferRecordPort =
        transferRecordDao
}