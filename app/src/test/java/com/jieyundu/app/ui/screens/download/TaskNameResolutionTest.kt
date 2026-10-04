// 文件：TaskNameResolutionTest.kt
// 职责：任务名来源优先级的单元测试（修复「重新下载后任务名变未命名」· JYD-P1B-2026-10-04）
// 依赖：JUnit4、resolveTaskName
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 任务名解析优先级的单元测试。
 *
 * 背景（Owner 装机反馈）：点「重新下载」后任务名变成「未命名」。
 * 真因是 UI 只从**进程内**的会话登记表取名字，进程重启后该表为空便回落到「未命名」——
 * P1-1 已把任务名持久化到 Room，但界面从未读取。本测试锁定修复后的优先级：
 * **持久化（Room）> 会话登记 > null**。
 */
class TaskNameResolutionTest {

    @Test
    fun `persisted name wins over session name`() {
        assertEquals(
            "电影.mp4",
            resolveTaskName(persistedName = "电影.mp4", sessionName = "旧名字.mp4")
        )
    }

    @Test
    fun `persisted name is used when session registry is empty after restart`() {
        // 这条就是本次 bug 的回归用例：重启后会话登记表为空，名字必须仍能显示。
        assertEquals(
            "电影.mp4",
            resolveTaskName(persistedName = "电影.mp4", sessionName = null)
        )
    }

    @Test
    fun `session name is used when persisted name missing`() {
        assertEquals(
            "电影.mp4",
            resolveTaskName(persistedName = null, sessionName = "电影.mp4")
        )
    }

    @Test
    fun `blank persisted name falls back to session name`() {
        assertEquals(
            "电影.mp4",
            resolveTaskName(persistedName = "   ", sessionName = "电影.mp4")
        )
    }

    @Test
    fun `returns null when neither source has a name`() {
        assertNull(resolveTaskName(persistedName = null, sessionName = null))
        assertNull(resolveTaskName(persistedName = "", sessionName = "  "))
    }
}
