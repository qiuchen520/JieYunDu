// 文件：TransferRecordGuardTest.kt
// 职责：转存登记持久化后守卫水合的单元测试（P1-3 · JYD-P1-2026-10-04）
// 依赖：JUnit4、TempFolderGuard
// 协议：AGPL-3.0

package com.jieyundu.app.domain.transfer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TempFolderGuard] 的「重启后水合」行为测试。
 *
 * 背景（P1-3）：守卫的放行集合原本只在进程内；把转存登记落库后，App 启动时会用
 * `registerAll(...)` 把重启前的副本重新登记进来。本测试验证水合后：
 * 重启前产生的副本重新变为「可删」，而未登记的 fid 仍保持默认拒绝。
 */
class TransferRecordGuardTest {

    private companion object {
        /** 重启前转存产生的副本 fid（模拟从 transfer_records 读回）。 */
        const val PERSISTED_FID = "persisted-transfer-fid"

        /** 重启前临时目录 fid。 */
        const val PERSISTED_DIR_FID = "persisted-temp-dir-fid"

        /** 用户自己的文件 fid。 */
        const val USER_FID = "user-own-file-fid"
    }

    @Test
    fun `hydrated transfer record becomes deletable after restart`() {
        // 模拟 Application 启动期水合：把库里读回的 fid 与目录 fid 登记进守卫。
        TempFolderGuard.registerAll(listOf(PERSISTED_FID))
        TempFolderGuard.register(PERSISTED_DIR_FID)
        try {
            assertTrue(
                TempFolderGuard.mayDeleteFromTemp(
                    fid = PERSISTED_FID,
                    tempFolderFid = PERSISTED_DIR_FID
                )
            )
        } finally {
            TempFolderGuard.unregister(PERSISTED_FID)
            TempFolderGuard.unregister(PERSISTED_DIR_FID)
        }
    }

    @Test
    fun `parent directory fid from persistence is recognised as temp folder`() {
        TempFolderGuard.register(PERSISTED_DIR_FID)
        try {
            // 已知父目录 fid = 持久化的临时目录 fid → 允许删除其下文件。
            assertTrue(
                TempFolderGuard.mayDeleteFromTemp(
                    fid = "child-fid",
                    name = "电影.mp4",
                    pdirFid = PERSISTED_DIR_FID,
                    tempFolderFid = PERSISTED_DIR_FID
                )
            )
        } finally {
            TempFolderGuard.unregister(PERSISTED_DIR_FID)
        }
    }

    @Test
    fun `unregistered user file stays rejected after hydration`() {
        TempFolderGuard.registerAll(listOf(PERSISTED_FID))
        try {
            assertFalse(
                TempFolderGuard.mayDeleteFromTemp(
                    fid = USER_FID,
                    name = "我的报告.docx",
                    pdirFid = "0"
                )
            )
        } finally {
            TempFolderGuard.unregister(PERSISTED_FID)
        }
    }

    @Test
    fun `record deletion removes hydrated permission`() {
        TempFolderGuard.register(PERSISTED_FID)
        TempFolderGuard.unregister(PERSISTED_FID)
        assertFalse(TempFolderGuard.mayDeleteFromTemp(fid = PERSISTED_FID))
    }
}
