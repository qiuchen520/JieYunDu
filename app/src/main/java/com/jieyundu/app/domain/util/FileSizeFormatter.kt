// 文件：FileSizeFormatter.kt
// 职责：把字节数格式化为人类可读的大小与速度文本
// 依赖：无
// 协议：AGPL-3.0

package com.jieyundu.app.domain.util

import java.util.Locale

/**
 * 文件大小 / 下载速度格式化工具。
 *
 * 说明：
 * - 单位符号均为 ASCII，展示文案不涉及中文，符合 C5；
 * - 结果固定使用 [Locale.US] 格式化，避免不同系统语言下小数点与千分位不一致。
 */
object FileSizeFormatter {

    /** 进制步长（1 KiB = 1024 B）。 */
    private const val STEP = 1024.0

    /** 单位表，索引与进制阶数对应。 */
    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    /** 大小未知时的占位文本。 */
    const val UNKNOWN = "--"

    /**
     * 格式化字节数为可读大小，例如 `1.50 MB`。
     *
     * @param sizeBytes 字节数；小于 0 表示未知。
     * @return 可读文本，未知时返回 [UNKNOWN]。
     */
    fun format(sizeBytes: Long): String {
        if (sizeBytes < 0L) return UNKNOWN
        if (sizeBytes < STEP) return "$sizeBytes ${UNITS[0]}"
        var value = sizeBytes.toDouble()
        var unitIndex = 0
        while (value >= STEP && unitIndex < UNITS.lastIndex) {
            value /= STEP
            unitIndex++
        }
        return String.format(Locale.US, "%.2f %s", value, UNITS[unitIndex])
    }

    /**
     * 格式化下载速度，例如 `1.50 MB/s`。
     *
     * @param bytesPerSecond 每秒字节数；小于 0 表示未知。
     * @return 可读文本，未知时返回 [UNKNOWN]。
     */
    fun formatSpeed(bytesPerSecond: Long): String =
        if (bytesPerSecond < 0L) UNKNOWN else format(bytesPerSecond) + "/s"
}
