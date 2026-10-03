// 文件：TempFolderManager.kt
// 职责：登记本账号中「转存产生的待清理文件 fid」（本批不创建目录，自动清理归阶段 13）
// 依赖：javax.inject、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.transfer

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * 临时转存目录管理器。
 *
 * 本批（解析链路修复【修订 JYD-PARSE-2026-10-03】）范围：仅登记「本次转存产生的
 * 本账号文件 fid」，供阶段 13 的「下载完成后自动清理临时文件」使用。
 *
 * **本批不创建临时目录**：创建目录需要夸克「创建文件夹」接口，抓包文档未提供
 * （R3 / D7：不得编造），故转存先落在网盘根目录；`.极云渡临时` 目录机制连同
 * 自动清理一并在阶段 13 实现。
 *
 * 线程安全：基于 [ConcurrentHashMap] 的并发集合，可在多协程下登记。
 */
@Singleton
class TempFolderManager @Inject constructor() {

    /** 待清理的转存文件 fid 集合。 */
    private val pendingCleanup: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * 登记一批待清理的转存文件 fid。
     *
     * @param fids 转存后得到的本账号文件 fid 列表；空白项忽略。
     */
    fun recordPendingCleanup(fids: List<String>) {
        fids.filter { fid -> fid.isNotBlank() }.forEach { fid ->
            if (pendingCleanup.add(fid)) {
                Timber.i("TempFolderManager pending cleanup fid=%s", fid)
            }
        }
    }

    /**
     * 取出当前全部待清理 fid 的快照。
     *
     * @return 待清理 fid 列表。
     */
    fun pendingCleanupFids(): List<String> = pendingCleanup.toList()

    /**
     * 移除已清理的 fid。
     *
     * @param fids 已完成清理的 fid 集合。
     */
    fun remove(fids: Collection<String>) {
        fids.forEach { fid -> pendingCleanup.remove(fid) }
    }
}