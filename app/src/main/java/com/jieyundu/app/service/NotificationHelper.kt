// 文件：NotificationHelper.kt
// 职责：创建下载通知渠道并构建下载进度/完成通知
// 依赖：Android Context、NotificationCompat、strings.xml
// 协议：AGPL-3.0

package com.jieyundu.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jieyundu.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * 下载通知构建与发送工具。
 *
 * 约定：
 * - 所有文案来自 `strings.xml`（C5）；
 * - 通知渠道在 [ensureChannel] 中幂等创建，Service 启动时调用一次即可。
 *
 * @param context 应用上下文（Hilt 注入，避免持有 Activity 引用）。
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * 幂等创建下载通知渠道（Android 8.0+ 必须）。
     */
    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID_DOWNLOAD)
        if (existing != null) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID_DOWNLOAD,
            context.getString(R.string.notification_channel_download_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_download_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 构建下载中通知。
     *
     * @param fileName 正在下载的文件名。
     * @param percent 进度百分比，0..100。
     * @return 通知对象。
     */
    fun buildProgressNotification(fileName: String, percent: Int): Notification {
        val bounded = percent.coerceIn(0, MAX_PERCENT)
        return baseBuilder(
            title = context.getString(R.string.notification_download_title),
            text = context.getString(R.string.notification_download_progress_text, bounded)
        )
            .setSubText(fileName)
            .setProgress(MAX_PERCENT, bounded, false)
            .setOngoing(true)
            .build()
    }

    /**
     * 构建下载完成通知。
     *
     * @param fileName 已完成的文件名。
     * @return 通知对象。
     */
    fun buildCompletedNotification(fileName: String): Notification =
        baseBuilder(
            title = context.getString(R.string.notification_download_completed_title),
            text = context.getString(R.string.notification_download_completed_text)
        )
            .setSubText(fileName)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

    /**
     * 发送通知（缺少通知权限时静默失败，不抛异常）。
     *
     * @param notificationId 通知 ID。
     * @param notification 通知对象。
     */
    fun notify(notificationId: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            return
        }
        try {
            manager.notify(notificationId, notification)
        } catch (securityException: SecurityException) {
            // 用户未授予 POST_NOTIFICATIONS：忽略即可，不影响下载
            Timber.e(securityException, "NotificationHelper notify failed")
        }
    }

    /**
     * 取消通知。
     *
     * @param notificationId 通知 ID。
     */
    fun cancel(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /**
     * 构造统一风格的基础通知构建器。
     *
     * @param title 标题文案。
     * @param text 内容文案。
     * @return 构建器。
     */
    private fun baseBuilder(title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

    companion object {
        /** 下载通知渠道 ID。 */
        const val CHANNEL_ID_DOWNLOAD = "jieyundu_download"

        /** 进度通知 ID。 */
        const val NOTIFICATION_ID_PROGRESS = 1001

        /** 百分比上限。 */
        const val MAX_PERCENT = 100
    }
}
