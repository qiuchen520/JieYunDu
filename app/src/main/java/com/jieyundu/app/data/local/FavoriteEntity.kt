// 文件：FavoriteEntity.kt
// 职责：收藏项的 Room 持久化实体
// 依赖：Room
// 协议：AGPL-3.0

package com.jieyundu.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 收藏记录实体。
 *
 * @property id 自增主键。
 * @property fid 文件 ID（各家网盘叫法不同）。
 * @property fileName 文件名。
 * @property netdiskType 网盘类型枚举名。
 * @property addedAt 收藏时间戳。
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "fid")
    val fid: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "netdisk_type")
    val netdiskType: String,
    @ColumnInfo(name = "added_at")
    val addedAt: Long
)
