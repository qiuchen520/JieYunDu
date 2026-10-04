// 文件：DownloadTimeFormatter.kt
// 职责：把下载剩余时间（秒）格式化为人类可读的时长文本
// 依赖：无
// 协议：AGPL-3.0

package com.jieyundu.app.domain.util

/**
 * 下载剩余时间格式化工具（【JYD-DLSPEED-2026-10-04】Owner 要求①）。
 *
 * 说明：
 * - 单位符号与分隔符均为 ASCII（`d`/`h`/`min`/`s`），展示文案不涉及中文，符合 C5；
 * - 秒数换算为整秒取整，不做四舍五入，避免出现「剩余 0 秒」而实际尚未完成；
 * - 超过一天时降级为「N d N h」两段式，不再细分，避免文本过长撑破列表项。
 */
object DownloadTimeFormatter {

    /** 未知时长时的占位文本（与 [FileSizeFormatter.UNKNOWN] 保持一致）。 */
    const val UNKNOWN = "--"

    /** 每分钟秒数。 */
    private const val SECONDS_PER_MINUTE = 60L

    /** 每小时秒数。 */
    private const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE

    /** 每天秒数。 */
    private const val SECONDS_PER_DAY = 24L * SECONDS_PER_HOUR

    /**
     * 格式化剩余秒数为可读时长，例如 `3 min 20 s`。
     *
     * @param seconds 剩余秒数；小于 0 表示未知（速度未知 / 总大小未知时由调用方传入 -1）。
     * @return 可读文本，未知时返回 [UNKNOWN]。
     */
    fun formatRemaining(seconds: Long): String {
        if (seconds < 0L) return UNKNOWN
        if (seconds >= SECONDS_PER_DAY) {
            val days = seconds / SECONDS_PER_DAY
            val hours = (seconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR
            return "$days d $hours h"
        }
        val hours = seconds / SECONDS_PER_HOUR
        val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val remainingSeconds = seconds % SECONDS_PER_MINUTE
        return if (hours > 0L) {
            "$hours h $minutes min"
        } else if (minutes > 0L) {
            "$minutes min $remainingSeconds s"
        } else {
            "$remainingSeconds s"
        }
    }

    /**
     * 由「剩余字节数 ÷ 当前速度」推算剩余秒数。
     *
     * @param remainingBytes 剩余字节数（总量 − 已下载）；未知时传入 -1。
     * @param speedBytesPerSecond 当前瞬时速度；非正数表示未知或已停滞。
     * @return 剩余秒数；无法推算时返回 -1。
     */
    fun remainingSeconds(remainingBytes: Long, speedBytesPerSecond: Long): Long =
        if (remainingBytes < 0L || speedBytesPerSecond <= 0L) {
            -1L
        } else {
            remainingBytes / speedBytesPerSecond
        }
}
