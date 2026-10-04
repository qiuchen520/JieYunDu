// 文件：TransferRecordPort.kt
// 职责：定义「转存副本登记」的持久化端口（domain 不反向依赖 data 层）
// 依赖：无
// 协议：AGPL-3.0

package com.jieyundu.app.domain.transfer

/**
 * 一条「转存副本」登记（【JYD-P1-2026-10-04】P1-3）。
 *
 * @property fid 转存副本在本账号中的 fid。
 * @property fileName 副本文件名（诊断 / 日志用）。
 * @property dirFid 副本所在目录 fid（即临时目录 fid）。
 * @property netdiskType 网盘类型枚举名。
 */
data class TransferRecord(
    val fid: String,
    val fileName: String? = null,
    val dirFid: String? = null,
    val netdiskType: String? = null
)

/**
 * 转存副本登记端口。
 *
 * 存在理由：删除安全守卫（[TempFolderGuard]）的放行依据之一就是「该 fid 由本 App 转存产生」。
 * 该事实必须**跨进程重启存活**，否则重启后守卫无法识别重启前产生的副本：
 * 既清不掉遗留副本，也可能因信息缺失而拒绝清理。端口由 data 层的 Room DAO 实现，
 * domain 层只依赖本接口。
 */
interface TransferRecordPort {

    /**
     * 写入或更新一条转存登记。
     *
     * @param record 转存记录。
     */
    suspend fun upsert(record: TransferRecord)

    /**
     * 查询全部转存登记。
     *
     * @return 转存记录列表。
     */
    suspend fun queryAll(): List<TransferRecord>

    /**
     * 删除一条转存登记。
     *
     * @param fid 转存副本 fid。
     */
    suspend fun delete(fid: String)
}
