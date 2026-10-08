// 文件：AppSettingsStore.kt
// 职责：应用级设置的持久化存储（下载并发数 + 下载目录模式/自定义路径）
// 依赖：SharedPreferences、DownloadTask 常量、Hilt
// 协议：AGPL-3.0

package com.jieyundu.app.data.settings

import android.content.Context
import com.jieyundu.app.R
import com.jieyundu.app.domain.downloader.DownloadSettingsPort
import com.jieyundu.app.domain.downloader.DownloadTask
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 下载目录模式（B2 功能①，Owner 拍板 A1/A3 组合）。
 *
 * @property PUBLIC_DOWNLOADS 默认：写入公共 `Download/极云渡/`，无需任何存储权限（走 MediaStore，A3）。
 * @property CUSTOM 仅在用户主动「更改目录」后启用：写入用户指定的真实路径，需要「所有文件访问」权限（A1）。
 */
enum class DownloadDirectoryMode {
    /** 公共下载目录（默认，A3）。 */
    PUBLIC_DOWNLOADS,

    /** 用户自定义目录（需 A1 权限）。 */
    CUSTOM;

    companion object {
        /**
         * 由持久化字符串还原模式。
         *
         * @param raw 持久化的枚举名；非法或为空时回退默认模式。
         * @return 下载目录模式。
         */
        fun fromPersisted(raw: String?): DownloadDirectoryMode =
            entries.firstOrNull { it.name == raw } ?: PUBLIC_DOWNLOADS
    }
}

/**
 * 应用设置存储。
 *
 * 说明：用 [android.content.SharedPreferences] 做轻量持久化（无需引入 DataStore / Room）；
 * 并发数用 [StateFlow] 暴露以便设置页与首页实时响应。所有读写都做了范围收敛，避免脏值。
 *
 * 存储键（内部实现细节，不对外暴露）：
 * - `chunk_count`：下载并发分片数；
 * - `download_dir_mode`：下载目录模式；
 * - `download_dir_path`：自定义目录真实路径。
 *
 * @param context 应用上下文（仅用于取 SharedPreferences 与字符串资源）。
 */
@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext context: Context
) : DownloadSettingsPort {

    private val appContext: Context = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _chunkCount = MutableStateFlow(
        prefs.getInt(KEY_CHUNK_COUNT, DownloadTask.DEFAULT_CHUNK_COUNT)
            .coerceIn(DownloadTask.MIN_CHUNK_COUNT, DownloadTask.MAX_CHUNK_COUNT)
    )

    /** 当前下载并发分片数（32..512，默认 64）。 */
    val chunkCount: StateFlow<Int> = _chunkCount.asStateFlow()

    /**
     * 设置下载并发分片数（越界自动收敛到 32..512）。
     *
     * @param count 期望的并发分片数。
     */
    fun setChunkCount(count: Int) {
        val clamped = count.coerceIn(DownloadTask.MIN_CHUNK_COUNT, DownloadTask.MAX_CHUNK_COUNT)
        if (clamped == _chunkCount.value) return
        prefs.edit().putInt(KEY_CHUNK_COUNT, clamped).apply()
        _chunkCount.value = clamped
    }

    private val _maxConcurrentTasks = MutableStateFlow(
        prefs.getInt(KEY_MAX_CONCURRENT_TASKS, DownloadTask.DEFAULT_MAX_CONCURRENT_TASKS)
            .coerceIn(
                DownloadTask.MIN_MAX_CONCURRENT_TASKS,
                DownloadTask.MAX_MAX_CONCURRENT_TASKS
            )
    )

    /** 最大同时下载任务数（C1：1..5，默认 1）。 */
    val maxConcurrentTasks: StateFlow<Int> = _maxConcurrentTasks.asStateFlow()

    /**
     * 设置最大同时下载任务数（C1，越界自动收敛到 1..5）。
     *
     * @param count 期望的同时任务数。
     */
    fun setMaxConcurrentTasks(count: Int) {
        val clamped = count.coerceIn(
            DownloadTask.MIN_MAX_CONCURRENT_TASKS,
            DownloadTask.MAX_MAX_CONCURRENT_TASKS
        )
        if (clamped == _maxConcurrentTasks.value) return
        prefs.edit().putInt(KEY_MAX_CONCURRENT_TASKS, clamped).apply()
        _maxConcurrentTasks.value = clamped
    }

    private val _maxTaskRetries = MutableStateFlow(
        prefs.getInt(KEY_MAX_TASK_RETRIES, DownloadTask.DEFAULT_MAX_TASK_RETRIES)
            .coerceIn(DownloadTask.MIN_MAX_TASK_RETRIES, DownloadTask.MAX_MAX_TASK_RETRIES)
    )

    /** 失败自动重试次数（C1：0..5，默认 3）。 */
    val maxTaskRetries: StateFlow<Int> = _maxTaskRetries.asStateFlow()

    /**
     * 设置失败自动重试次数（C1，越界自动收敛到 0..5）。
     *
     * @param count 期望的重试次数。
     */
    fun setMaxTaskRetries(count: Int) {
        val clamped = count.coerceIn(
            DownloadTask.MIN_MAX_TASK_RETRIES,
            DownloadTask.MAX_MAX_TASK_RETRIES
        )
        if (clamped == _maxTaskRetries.value) return
        prefs.edit().putInt(KEY_MAX_TASK_RETRIES, clamped).apply()
        _maxTaskRetries.value = clamped
    }

    private val _speedLimitBytesPerSecond = MutableStateFlow(
        prefs.getLong(KEY_SPEED_LIMIT, DownloadTask.DEFAULT_SPEED_LIMIT_BYTES_PER_SECOND)
            .coerceAtLeast(0L)
    )

    /** 下载限速（C1：字节/秒；0 表示不限速）。 */
    val speedLimitBytesPerSecond: StateFlow<Long> = _speedLimitBytesPerSecond.asStateFlow()

    /**
     * 设置下载限速（C1）。
     *
     * @param bytesPerSecond 期望限速，单位字节/秒；0 或负数表示不限速。
     */
    fun setSpeedLimitBytesPerSecond(bytesPerSecond: Long) {
        val clamped = bytesPerSecond.coerceAtLeast(0L)
        if (clamped == _speedLimitBytesPerSecond.value) return
        prefs.edit().putLong(KEY_SPEED_LIMIT, clamped).apply()
        _speedLimitBytesPerSecond.value = clamped
    }

    /**
     * 后台保活：锁屏后是否保持下载（C2 第 1 条）。
     *
     * 说明：默认开启。开启时下载任务运行期间持有 `PARTIAL_WAKE_LOCK`
     * （见 `DownloadWakeLockManager`）。
     */
    private val _keepDownloadingOnLock = MutableStateFlow(
        prefs.getBoolean(KEY_KEEP_DOWNLOADING_ON_LOCK, DEFAULT_KEEP_DOWNLOADING_ON_LOCK)
    )

    /** 「锁屏后保持下载」开关的只读流。 */
    val keepDownloadingOnLock: StateFlow<Boolean> = _keepDownloadingOnLock.asStateFlow()

    /**
     * 设置「锁屏后保持下载」（C2 第 1 条）。
     *
     * @param enabled 是否开启。
     */
    fun setKeepDownloadingOnLock(enabled: Boolean) {
        if (enabled == _keepDownloadingOnLock.value) return
        prefs.edit().putBoolean(KEY_KEEP_DOWNLOADING_ON_LOCK, enabled).apply()
        _keepDownloadingOnLock.value = enabled
    }

    /**
     * 通知栏下载进度开关（C2 第 2 条）。
     *
     * 说明：默认开启。关闭时不发布进度通知，但**前台服务仍然运行**（保活不受影响），
     * 完成通知也一并静默，避免用户明确关闭后仍被打扰。
     */
    private val _downloadNotificationEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_DOWNLOAD_NOTIFICATION_ENABLED, DEFAULT_DOWNLOAD_NOTIFICATION_ENABLED)
    )

    /** 「通知栏下载进度」开关的只读流。 */
    val downloadNotificationEnabled: StateFlow<Boolean> = _downloadNotificationEnabled.asStateFlow()

    /**
     * 设置「通知栏下载进度」（C2 第 2 条）。
     *
     * @param enabled 是否开启。
     */
    fun setDownloadNotificationEnabled(enabled: Boolean) {
        if (enabled == _downloadNotificationEnabled.value) return
        prefs.edit().putBoolean(KEY_DOWNLOAD_NOTIFICATION_ENABLED, enabled).apply()
        _downloadNotificationEnabled.value = enabled
    }

    /** [DownloadSettingsPort]：当前最大同时下载任务数。 */
    override fun currentMaxConcurrentTasks(): Int = _maxConcurrentTasks.value

    /** [DownloadSettingsPort]：当前限速（字节/秒，0 = 不限速）。 */
    override fun currentSpeedLimitBytesPerSecond(): Long = _speedLimitBytesPerSecond.value

    /** [DownloadSettingsPort]：当前失败自动重试次数。 */
    override fun currentMaxTaskRetries(): Int = _maxTaskRetries.value

    /** 当前下载目录模式（默认 [DownloadDirectoryMode.PUBLIC_DOWNLOADS]）。 */
    val downloadDirectoryMode: DownloadDirectoryMode
        get() = DownloadDirectoryMode.fromPersisted(prefs.getString(KEY_DIR_MODE, null))

    /** 用户自定义目录的真实路径；未设置时为 null。 */
    val customDirectoryPath: String?
        get() = prefs.getString(KEY_DIR_PATH, null)?.takeIf { it.isNotBlank() }

    /**
     * 启用自定义下载目录。
     *
     * @param realPath 用户选定目录的真实绝对路径（已由目录解析器转换）。
     */
    fun setCustomDirectory(realPath: String) {
        prefs.edit()
            .putString(KEY_DIR_MODE, DownloadDirectoryMode.CUSTOM.name)
            .putString(KEY_DIR_PATH, realPath)
            .apply()
    }

    /** 恢复默认下载目录（公共 `Download/极云渡/`，A3）。 */
    fun resetDownloadDirectory() {
        prefs.edit()
            .putString(KEY_DIR_MODE, DownloadDirectoryMode.PUBLIC_DOWNLOADS.name)
            .remove(KEY_DIR_PATH)
            .apply()
    }

    /**
     * 公共下载目录下的应用专属子目录名。
     *
     * 说明：取自字符串资源以满足 C5（禁止硬编码中文）。
     *
     * @return 子目录名，例如「极云渡」。
     */
    fun publicFolderName(): String = appContext.getString(R.string.download_public_folder_name)

    private companion object {
        /** SharedPreferences 文件名。 */
        const val PREFS_NAME = "jieyundu_settings"

        /** 并发分片数键。 */
        const val KEY_CHUNK_COUNT = "chunk_count"

        /** 下载目录模式键。 */
        const val KEY_DIR_MODE = "download_dir_mode"

        /** 自定义目录路径键。 */
        const val KEY_DIR_PATH = "download_dir_path"

        /** 最大同时下载任务数键（C1）。 */
        const val KEY_MAX_CONCURRENT_TASKS = "max_concurrent_tasks"

        /** 失败自动重试次数键（C1）。 */
        const val KEY_MAX_TASK_RETRIES = "max_task_retries"

        /** 下载限速键（C1，字节/秒）。 */
        const val KEY_SPEED_LIMIT = "speed_limit_bytes_per_second"

        /** 锁屏保持下载键（C2 第 1 条）。 */
        const val KEY_KEEP_DOWNLOADING_ON_LOCK = "keep_downloading_on_lock"

        /** 通知栏下载进度键（C2 第 2 条）。 */
        const val KEY_DOWNLOAD_NOTIFICATION_ENABLED = "download_notification_enabled"

        /** 锁屏保持下载默认值（C2：默认开）。 */
        const val DEFAULT_KEEP_DOWNLOADING_ON_LOCK = true

        /** 通知栏下载进度默认值（C2：默认开）。 */
        const val DEFAULT_DOWNLOAD_NOTIFICATION_ENABLED = true
    }
}
