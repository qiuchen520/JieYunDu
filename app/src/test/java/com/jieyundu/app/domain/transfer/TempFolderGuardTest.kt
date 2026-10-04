// 文件：TempFolderGuardTest.kt
// 职责：网盘侧删除安全边界（TempFolderGuard）的单元测试（P0 · JYD-DELSAFE-2026-10-04）
// 依赖：JUnit4、TempFolderGuard
// 协议：AGPL-3.0

package com.jieyundu.app.domain.transfer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TempFolderGuard] 的单元测试。
 *
 * 覆盖 bug 指令要求的三种情况：临时目录内 → 允许删；用户自己网盘的文件 → 禁止删；
 * 路径相似但不匹配 → 禁止删。另覆盖临时目录自身（仅空目录可删）与默认拒绝语义。
 */
class TempFolderGuardTest {

    private companion object {
        /** 测试用临时目录 fid。 */
        const val TEMP_FID = "temp-fid-0001"

        /** 测试用用户文件 fid。 */
        const val USER_FID = "user-fid-9999"
    }

    // --- 情况 1：临时目录内的文件 → 允许删 ---

    @Test
    fun `allows delete when path contains temp folder marker`() {
        assertTrue(
            TempFolderGuard.mayDeleteFromTemp(
                fid = "fid-in-temp",
                name = ".极云渡临时/电影.mp4"
            )
        )
    }

    @Test
    fun `allows delete when path is nested under temp folder`() {
        assertTrue(
            TempFolderGuard.mayDeleteFromTemp(
                fid = "fid-in-temp",
                name = "/我的网盘/.极云渡临时/电影.mp4"
            )
        )
    }

    @Test
    fun `allows delete when parent fid equals temp folder fid`() {
        assertTrue(
            TempFolderGuard.mayDeleteFromTemp(
                fid = "fid-in-temp",
                name = "电影.mp4",
                pdirFid = TEMP_FID,
                tempFolderFid = TEMP_FID
            )
        )
    }

    @Test
    fun `allows delete when fid was registered by transfer flow`() {
        TempFolderGuard.register("transferred-fid")
        try {
            assertTrue(TempFolderGuard.mayDeleteFromTemp(fid = "transferred-fid"))
        } finally {
            TempFolderGuard.unregister("transferred-fid")
        }
    }

    // --- 情况 2：用户自己网盘的文件 → 禁止删 ---

    @Test
    fun `rejects delete for user file in root with no signals`() {
        assertFalse(
            TempFolderGuard.mayDeleteFromTemp(
                fid = USER_FID,
                name = "我的报告.docx",
                pdirFid = "0"
            )
        )
    }

    @Test
    fun `rejects delete for user file inside a normal folder`() {
        assertFalse(
            TempFolderGuard.mayDeleteFromTemp(
                fid = USER_FID,
                name = "我的报告.docx",
                pdirFid = "user-folder-fid",
                tempFolderFid = TEMP_FID
            )
        )
    }

    @Test
    fun `rejects delete when fid is blank`() {
        assertFalse(TempFolderGuard.mayDeleteFromTemp(fid = ""))
        assertFalse(TempFolderGuard.mayDeleteFromTemp(fid = null))
        assertFalse(TempFolderGuard.mayDeleteFromTemp(fid = "   "))
    }

    // --- 情况 3：路径相似但不匹配 → 禁止删 ---

    @Test
    fun `rejects delete for lookalike folder names`() {
        val lookalikes = listOf(
            ".极云渡临时文件/报告.docx",
            "极云渡临时/报告.docx",
            "我的.极云渡临时x/报告.docx",
            ".极云渡临时x",
            "备份.极云渡临时"
        )
        lookalikes.forEach { name ->
            assertFalse(
                "should reject: $name",
                TempFolderGuard.mayDeleteFromTemp(fid = USER_FID, name = name)
            )
        }
    }

    @Test
    fun `rejects delete for lookalike parent fid`() {
        assertFalse(
            TempFolderGuard.mayDeleteFromTemp(
                fid = USER_FID,
                name = "报告.docx",
                pdirFid = TEMP_FID + "-suffix",
                tempFolderFid = TEMP_FID
            )
        )
    }

    // --- 临时目录自身：仅空目录可删 ---

    @Test
    fun `allows deleting temp folder itself only when empty`() {
        assertTrue(
            TempFolderGuard.mayDeleteFromTemp(
                fid = TEMP_FID,
                name = TempFolderGuard.TEMP_FOLDER_MARKER,
                tempFolderFid = TEMP_FID,
                isDirectory = true,
                isEmptyFolder = true
            )
        )
        assertFalse(
            TempFolderGuard.mayDeleteFromTemp(
                fid = TEMP_FID,
                name = TempFolderGuard.TEMP_FOLDER_MARKER,
                tempFolderFid = TEMP_FID,
                isDirectory = true,
                isEmptyFolder = false
            )
        )
    }

    // --- 用户主动删除（网盘管理页「删除」按钮）：走独立出口，语义与自动清理不同 ---

    @Test
    fun `user initiated delete allows own file outside temp folder`() {
        // 管理页删除的是用户自己的文件，不应被「仅临时目录可删」的自动清理规则拦住。
        assertTrue(
            TempFolderGuard.mayDeleteUserInitiated(fid = USER_FID, userInitiated = true)
        )
    }

    @Test
    fun `user initiated delete rejected without explicit confirmation`() {
        // 只有 UI 确认弹窗之后才传 true；任何「顺带」调用拿不到放行。
        assertFalse(
            TempFolderGuard.mayDeleteUserInitiated(fid = USER_FID, userInitiated = false)
        )
    }

    @Test
    fun `user initiated delete rejected for blank fid`() {
        assertFalse(TempFolderGuard.mayDeleteUserInitiated(fid = "", userInitiated = true))
        assertFalse(TempFolderGuard.mayDeleteUserInitiated(fid = null, userInitiated = true))
    }

    @Test
    fun `user initiated delete does not loosen automatic cleanup rules`() {
        // 关键回归：用户删除出口不得把自动清理的默认拒绝语义松掉。
        assertTrue(TempFolderGuard.mayDeleteUserInitiated(USER_FID, userInitiated = true))
        assertFalse(TempFolderGuard.mayDeleteFromTemp(fid = USER_FID, name = "我的报告.docx", pdirFid = "0"))
    }

    @Test
    fun `rejects deleting a non-empty lookalike directory`() {
        assertFalse(
            TempFolderGuard.mayDeleteFromTemp(
                fid = "some-other-dir",
                name = "我的文件夹",
                isDirectory = true,
                isEmptyFolder = false
            )
        )
    }
}
