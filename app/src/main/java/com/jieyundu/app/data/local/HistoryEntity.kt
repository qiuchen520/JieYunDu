// 文件：HistoryEntity.kt
// 职责：下载历史的 Room 持久化实体
// 依赖：Room
// 协议：AGPL-3.0

package com.jieyundu.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 下载历史记录实体。
 *
 * @property id 自增主键。
 * @property fileName 文件名。
 * @property fileSize 文件大小（字节）。
 * @property netdiskType 网盘类型枚举名。
 * @property shareTitle 分享标题。
 * @property localPath 本地保存路径。
 * @property downloadedAt 完成时间戳。
 */
@Entity(tableName = "download_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "file_size")
    val fileSize: Long,
    @ColumnInfo(name = "netdisk_type")
    val netdiskType: String,
    @ColumnInfo(name = "share_title")
    val shareTitle: String,
    @ColumnInfo(name = "local_path")
    val localPath: String,
    @ColumnInfo(name = "downloaded_at")
    val downloadedAt: Long
)
