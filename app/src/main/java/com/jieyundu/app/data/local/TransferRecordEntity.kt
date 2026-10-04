// 文件：TransferRecordEntity.kt
// 职责：转存副本登记的 Room 持久化实体（重启后仍能识别临时目录副本）
// 依赖：TransferRecord、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jieyundu.app.domain.transfer.TransferRecord

/**
 * 转存副本持久化实体（【JYD-P1-2026-10-04】P1-3）。
 *
 * 存在理由：删除安全守卫（`TempFolderGuard`）原先只依赖**进程内**的已登记副本集合，
 * App 重启后无法识别重启前转存产生的副本，导致「手动清理」在重启后清不掉遗留副本。
 * 本表把「哪个文件是本 App 转存出来的」这一事实落库，重启后据此恢复守卫的放行集合。
 *
 * @property fid 转存副本在本账号中的 fid（主键）。
 * @property fileName 副本文件名（诊断 / 日志用）。
 * @property dirFid 副本所在目录 fid（即临时目录 fid；用于守卫的目录比对）。
 * @property netdiskType 网盘类型枚举名（区分夸克 / UC 等，便于后续按网盘清理）。
 * @property createdAt 登记时间戳（毫秒）。
 */
@Entity(
    tableName = "transfer_records",
    indices = [Index(value = ["dir_fid"])]
)
data class TransferRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "fid")
    val fid: String,
    @ColumnInfo(name = "file_name")
    val fileName: String?,
    @ColumnInfo(name = "dir_fid")
    val dirFid: String?,
    @ColumnInfo(name = "netdisk_type")
    val netdiskType: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {

    /**
     * 转换为领域层记录。
     *
     * @return 转存记录。
     */
    fun toRecord(): TransferRecord = TransferRecord(
        fid = fid,
        fileName = fileName,
        dirFid = dirFid,
        netdiskType = netdiskType
    )

    companion object {

        /**
         * 由领域记录构造实体。
         *
         * @param record 转存记录。
         * @param createdAt 登记时间戳，默认取当前时间。
         * @return 实体。
         */
        fun fromRecord(
            record: TransferRecord,
            createdAt: Long = System.currentTimeMillis()
        ): TransferRecordEntity = TransferRecordEntity(
            fid = record.fid,
            fileName = record.fileName,
            dirFid = record.dirFid,
            netdiskType = record.netdiskType,
            createdAt = createdAt
        )
    }
}
