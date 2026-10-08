// 文件：TransferRecordDao.kt
// 职责：转存副本登记表的数据访问对象，实现 domain 的 TransferRecordPort
// 依赖：Room、TransferRecordEntity、TransferRecordPort、TransferRecord
// 协议：AGPL-3.0

package com.jieyundu.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jieyundu.app.domain.transfer.TransferRecordPort
import com.jieyundu.app.domain.transfer.TransferRecord

/**
 * 转存副本登记 DAO（【JYD-P1-2026-10-04】P1-3）。
 *
 * 说明：本 DAO 直接实现 [TransferRecordPort]，使 domain 层（临时目录管理器 / 删除守卫）
 * 无需依赖 data 层类型即可完成副本登记的持久化与恢复。
 */
@Dao
abstract class TransferRecordDao : TransferRecordPort {

    /**
     * 插入或覆盖一条转存登记。
     *
     * @param entity 登记实体。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertEntity(entity: TransferRecordEntity)

    /**
     * 查询全部登记（按登记时间正序）。
     *
     * @return 登记实体列表。
     */
    @Query("SELECT * FROM transfer_records ORDER BY created_at ASC")
    abstract suspend fun queryAllEntities(): List<TransferRecordEntity>

    /**
     * 按 fid 删除一条登记。
     *
     * @param fid 转存副本 fid。
     */
    @Query("DELETE FROM transfer_records WHERE fid = :fid")
    abstract suspend fun deleteByFid(fid: String)

    /**
     * 写入或更新一条转存登记（实现 [TransferRecordPort.upsert]）。
     *
     * @param record 转存记录。
     */
    override suspend fun upsert(record: TransferRecord) {
        upsertEntity(TransferRecordEntity.fromRecord(record))
    }

    /**
     * 查询全部转存登记（实现 [TransferRecordPort.queryAll]）。
     *
     * @return 转存记录列表。
     */
    override suspend fun queryAll(): List<TransferRecord> =
        queryAllEntities().map(TransferRecordEntity::toRecord)

    /**
     * 删除一条转存登记（实现 [TransferRecordPort.delete]）。
     *
     * @param fid 转存副本 fid。
     */
    override suspend fun delete(fid: String) {
        deleteByFid(fid)
    }
}
