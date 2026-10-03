// 文件：AppSettingsStore.kt
// 职责：应用级设置的持久化存储（下载并发数 + 下载目录模式/自定义路径）
// 依赖：SharedPreferences、DownloadTask 常量、Hilt
// 协议：AGPL-3.0

package com.jieyundu.app.data.settings

import android.content.Context
import androidx.annotation.StringRes
import com.jieyundu.app.R
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
) {

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

    /** 供设置页展示的自定义目录文案资源（用于「恢复默认」提示等）。 */
    @StringRes
    fun defaultDirectoryLabelRes(): Int = R.string.settings_download_dir_default_value

    private companion object {
        /** SharedPreferences 文件名。 */
        const val PREFS_NAME = "jieyundu_settings"

        /** 并发分片数键。 */
        const val KEY_CHUNK_COUNT = "chunk_count"

        /** 下载目录模式键。 */
        const val KEY_DIR_MODE = "download_dir_mode"

        /** 自定义目录路径键。 */
        const val KEY_DIR_PATH = "download_dir_path"
    }
}
