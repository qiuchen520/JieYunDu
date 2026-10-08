// 文件：DownloadSpeedLimiter.kt
// 职责：全局限速器——令牌桶 / 时间预约模型，供所有任务的所有分片共享
// 依赖：kotlinx.coroutines（Mutex / withLock / delay）
// 协议：AGPL-3.0

package com.jieyundu.app.domain.downloader

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// 【修订 JYD-DEBT3-2026-10-07】原为 DownloadEngine.kt 内的 private class，
// 拆文件后放宽为同包 internal（引擎持有实例，分片下载器按实例申请配额）。

/**
 * 全局下载限速器（C1：令牌桶 / 时间预约模型）。
 *
 * 所有任务的所有分片共享同一实例，从而保证「限速」作用于整个 App 的总出口，
 * 而非逐分片限速。策略：每申请 [acquire] 的字节数，按当前限速换算为应占用的时长，
 * 预约到 [nextFreeNanos] 之后；若预约时间在未来则挂起等待，实现平滑限速。
 *
 * 线程安全：由 [mutex] 串行化预约计算，临界区极短；等待在锁外进行，不阻塞其他分片。
 */
internal class SpeedLimiter {

    /** 预约计算锁。 */
    private val mutex = Mutex()

    /** 下一个可用时间点（单调时钟，纳秒）。 */
    private var nextFreeNanos: Long = 0L

    /**
     * 申请发送 [bytes] 字节的配额；超过限速时挂起到允许发送为止。
     *
     * @param bytes 本轮实际写入的字节数。
     * @param limitBytesPerSecond 当前限速，单位字节/秒；非正数表示不限速。
     */
    suspend fun acquire(bytes: Int, limitBytesPerSecond: Long) {
        if (limitBytesPerSecond <= 0L || bytes <= 0) {
            return
        }
        val waitNanos = mutex.withLock {
            val now = System.nanoTime()
            val grantedAt = if (nextFreeNanos < now) now else nextFreeNanos
            val costNanos = bytes.toLong() * NANOS_PER_SECOND / limitBytesPerSecond
            nextFreeNanos = grantedAt + costNanos
            grantedAt - now
        }
        if (waitNanos > 0L) {
            delay(waitNanos / NANOS_PER_MILLI)
        }
    }

    private companion object {
        /** 每秒纳秒数。 */
        const val NANOS_PER_SECOND = 1_000_000_000L

        /** 每毫秒纳秒数。 */
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
