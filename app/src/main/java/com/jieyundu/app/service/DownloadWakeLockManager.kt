// 文件：DownloadWakeLockManager.kt
// 职责：下载期间的 PARTIAL_WAKE_LOCK 管理（C2「锁屏后保持下载」）
// 依赖：PowerManager、Timber、Hilt
// 协议：AGPL-3.0

package com.jieyundu.app.service

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * 下载期间持有的 CPU 唤醒锁管理器（《要求.md》C2 第 1 条）。
 *
 * 行为：
 * - **懒持有**：只有在确实有下载任务运行时才 `acquire`，任务结束（完成 / 失败 / 暂停 / 取消）
 *   立即 `release`，不做常驻持有；
 * - **可重入**：前台服务可能并发处理多个任务，内部用计数维护，计数归零才真正释放；
 * - **可失败**：`PowerManager` 缺失或系统拒绝时只记日志、不抛异常（下载继续，仅退化为
 *   依赖前台服务保活）。
 *
 * 说明：`PARTIAL_WAKE_LOCK` 只保证 CPU 不休眠，不亮屏、不阻止用户关屏，因此不需要
 * `ACQUIRE_CAUSES_WAKEUP`；锁本身带超时兜底，避免异常路径下永久持有。
 */
@Singleton
class DownloadWakeLockManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val appContext: Context = context.applicationContext

    /** 已持有的锁计数（受 [lock] 保护）。 */
    private var acquireCount: Int = 0

    /** 当前持有的唤醒锁；未持有时为 null。 */
    private var wakeLock: PowerManager.WakeLock? = null

    /** 计数与锁对象的互斥保护（可能在主线程与服务协程间并发调用）。 */
    private val lock = Any()

    /**
     * 申请一次唤醒锁（可重入）。
     *
     * 说明：首次申请时创建并 `acquire`；重复申请仅累加计数。调用方必须在任务结束时配对调用
     * [release]（通常放在 `finally` 中），否则计数不归零、锁不会释放。
     */
    fun acquire() {
        synchronized(lock) {
            acquireCount++
            if (wakeLock?.isHeld == true) {
                return
            }
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager == null) {
                Timber.e("DownloadWakeLockManager: PowerManager unavailable")
                return
            }
            try {
                val created = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    WAKE_LOCK_TAG
                )
                created.setReferenceCounted(false)
                created.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
                wakeLock = created
                Timber.i("DownloadWakeLockManager acquired (count=%d)", acquireCount)
            } catch (exception: RuntimeException) {
                // 系统拒绝（如权限被回收）时退化为不持锁，下载继续由前台服务保活。
                Timber.e(exception, "DownloadWakeLockManager acquire failed")
            }
        }
    }

    /**
     * 释放一次唤醒锁（与 [acquire] 配对）；计数归零时真正释放。
     */
    fun release() {
        synchronized(lock) {
            if (acquireCount > 0) {
                acquireCount--
            }
            if (acquireCount > 0) {
                return
            }
            val held = wakeLock ?: return
            try {
                if (held.isHeld) {
                    held.release()
                }
            } catch (exception: RuntimeException) {
                Timber.e(exception, "DownloadWakeLockManager release failed")
            } finally {
                wakeLock = null
                Timber.i("DownloadWakeLockManager released")
            }
        }
    }

    private companion object {
        /** 唤醒锁标签（便于用 `adb shell dumpsys power` 排查）。 */
        const val WAKE_LOCK_TAG = "jieyundu:download"

        /**
         * 唤醒锁超时兜底：10 分钟。
         *
         * 说明：正常路径由 [release] 显式释放；超时仅用于「服务被系统杀死」等异常路径，
         * 避免锁泄漏导致耗电。超时后若仍在下载，前台服务保活机制仍然生效。
         */
        const val WAKE_LOCK_TIMEOUT_MILLIS = 10L * 60L * 1000L
    }
}
