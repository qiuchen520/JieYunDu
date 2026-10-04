// 文件：DownloadTimeFormatterTest.kt
// 职责：下载剩余时间格式化的单元测试（JYD-DLSPEED-2026-10-04）
// 依赖：JUnit4、DownloadTimeFormatter
// 协议：AGPL-3.0

package com.jieyundu.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [DownloadTimeFormatter] 的单元测试。
 *
 * 说明：纯函数、不触网，可直接在 JVM 上运行（与 CI 的 `testDebugUnitTest` 一致）。
 */
class DownloadTimeFormatterTest {

    @Test
    fun `formatRemaining returns unknown placeholder for negative seconds`() {
        assertEquals(DownloadTimeFormatter.UNKNOWN, DownloadTimeFormatter.formatRemaining(-1L))
    }

    @Test
    fun `formatRemaining formats seconds only below one minute`() {
        assertEquals("0 s", DownloadTimeFormatter.formatRemaining(0L))
        assertEquals("45 s", DownloadTimeFormatter.formatRemaining(45L))
    }

    @Test
    fun `formatRemaining formats minutes and seconds below one hour`() {
        assertEquals("3 min 20 s", DownloadTimeFormatter.formatRemaining(200L))
        assertEquals("1 min 0 s", DownloadTimeFormatter.formatRemaining(60L))
    }

    @Test
    fun `formatRemaining formats hours and minutes below one day`() {
        assertEquals("2 h 30 min", DownloadTimeFormatter.formatRemaining(9_000L))
    }

    @Test
    fun `formatRemaining formats days and hours above one day`() {
        assertEquals("1 d 1 h", DownloadTimeFormatter.formatRemaining(90_000L))
    }

    @Test
    fun `remainingSeconds returns unknown when speed is unknown or stalled`() {
        assertEquals(-1L, DownloadTimeFormatter.remainingSeconds(1_000L, -1L))
        assertEquals(-1L, DownloadTimeFormatter.remainingSeconds(1_000L, 0L))
    }

    @Test
    fun `remainingSeconds returns unknown when remaining bytes is unknown`() {
        assertEquals(-1L, DownloadTimeFormatter.remainingSeconds(-1L, 1_000L))
    }

    @Test
    fun `remainingSeconds divides remaining bytes by speed`() {
        assertEquals(10L, DownloadTimeFormatter.remainingSeconds(10_000L, 1_000L))
    }
}
