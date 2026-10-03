// 文件：PublicDownloadsPublisher.kt
// 职责：把已下载的文件发布到公共下载目录（Android 10+ 走 MediaStore；8/9 直写公共目录）
// 依赖：MediaStore / MediaScannerConnection / MimeTypeMap、Hilt
// 协议：AGPL-3.0

package com.jieyundu.app.data.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 公共下载目录发布器（B2 功能①，默认 A3 方案）。
 *
 * 说明：下载引擎把分片与合并结果写在应用私有目录（保证 `.part` 续传可控），下载完成后由本类
 * 把成品「发布」到公共 `Download/极云渡/`：
 * - **Android 10（API 29）及以上**：插入 [MediaStore.Downloads] 集合并写入 `RELATIVE_PATH`，
 *   全程无需存储权限，返回该媒体项的 `content://` Uri；
 * - **Android 8 / 9（API 26–28）**：直接写入公共目录并触发媒体扫描，返回真实文件路径。
 *
 * 返回的字符串即发布后的可访问位置：`content://` 用于分享 / 安装；绝对路径用于 `<29` 场景。
 *
 * @param context 应用上下文。
 */
@Singleton
class PublicDownloadsPublisher @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * 把源文件发布到公共下载目录下的 [folderName] 子目录。
     *
     * @param source 私有目录中的成品文件。
     * @param folderName 公共下载目录下的子目录名（如「极云渡」）。
     * @return 发布后的可访问位置（`content://` Uri 或绝对路径）；失败时返回 null。
     */
    suspend fun publish(source: File, folderName: String): String? = withContext(Dispatchers.IO) {
        if (!source.isFile) {
            Timber.w("PublicDownloadsPublisher: source not found: %s", source.name)
            return@withContext null
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishViaMediaStore(source, folderName)
            } else {
                publishLegacy(source, folderName)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Timber.e(exception, "PublicDownloadsPublisher: publish failed for %s", source.name)
            null
        }
    }

    /**
     * Android 10+ 走 MediaStore 发布。
     *
     * @param source 源文件。
     * @param folderName 子目录名。
     * @return 媒体项 Uri 字符串；插入或写入失败时 null。
     */
    private fun publishViaMediaStore(source: File, folderName: String): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeOf(source.name))
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + File.separator + folderName
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
        if (uri == null) {
            Timber.e("PublicDownloadsPublisher: MediaStore insert returned null")
            return null
        }
        resolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: run {
            Timber.e("PublicDownloadsPublisher: openOutputStream failed, dropping record")
            resolver.delete(uri, null, null)
            return null
        }
        val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(uri, ready, null, null)
        return uri.toString()
    }

    /**
     * Android 8 / 9 直写公共目录并触发扫描。
     *
     * @param source 源文件。
     * @param folderName 子目录名。
     * @return 目标文件绝对路径；创建目录或拷贝失败时 null。
     */
    private fun publishLegacy(source: File, folderName: String): String? {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val targetDir = File(publicDownloads, folderName)
        if (!targetDir.isDirectory && !targetDir.mkdirs()) {
            Timber.e("PublicDownloadsPublisher: cannot create %s", targetDir.absolutePath)
            return null
        }
        val target = File(targetDir, source.name)
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
        return target.absolutePath
    }

    /**
     * 依据文件名后缀推断 MIME 类型。
     *
     * @param fileName 文件名。
     * @return MIME 类型；无法识别时回退 `application/octet-stream`。
     */
    private fun mimeTypeOf(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: DEFAULT_MIME_TYPE
    }

    private companion object {
        /** 兜底 MIME 类型。 */
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
    }
}