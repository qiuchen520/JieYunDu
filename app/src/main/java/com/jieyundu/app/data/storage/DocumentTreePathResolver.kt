// 文件：DocumentTreePathResolver.kt
// 职责：把 SAF 目录选择器返回的 tree Uri 解析成真实文件系统绝对路径（B2 功能① / A1）
// 依赖：DocumentsContract、Environment
// 协议：AGPL-3.0

package com.jieyundu.app.data.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import timber.log.Timber

/**
 * SAF 目录树 Uri → 真实路径解析器。
 *
 * 背景（B2 功能①）：A1 方案要求下载到用户选定的**真实文件系统路径**，而系统目录选择器
 * （`ACTION_OPEN_DOCUMENT_TREE`）返回的是 `content://` 文档树 Uri。真实路径无法从 Uri 直接
 * 读取，只能从文档 ID（形如 `primary:Download/极云渡`）反推——本类负责这一「尽力而为」的转换。
 *
 * 支持形式：
 * - `primary:Download/foo` → `/storage/emulated/0/Download/foo`（主共享存储）；
 * - `raw:/storage/xxxx/foo` → `/storage/xxxx/foo`（已给真实路径）；
 * - `XXXX-XXXX:foo` → `/storage/XXXX-XXXX/foo`（可移除存储卷）。
 *
 * 局限：个别第三方 DocumentsProvider 的文档 ID 不含真实路径，此时返回 null，调用方应提示
 * 用户改用默认目录（不静默降级到错误路径）。
 */
object DocumentTreePathResolver {

    /**
     * 解析目录树 Uri 对应的真实路径。
     *
     * @param context 上下文（保留参数，便于后续扩展不同 provider 的解析）。
     * @param treeUri 目录选择器返回的 tree Uri。
     * @return 真实绝对路径；无法解析时返回 null。
     */
    fun resolve(context: Context, treeUri: Uri): String? {
        val documentId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (exception: IllegalArgumentException) {
            Timber.e(exception, "DocumentTreePathResolver: not a tree uri: %s", treeUri)
            return null
        }
        val segments = documentId.split(":", limit = LIMIT_TWO)
        if (segments.size < LIMIT_TWO) {
            Timber.w("DocumentTreePathResolver: unsupported documentId %s", documentId)
            return null
        }
        val volume = segments[0]
        val relative = segments[1]
        val root = when {
            volume.equals(VOLUME_PRIMARY, ignoreCase = true) ->
                Environment.getExternalStorageDirectory().absolutePath

            volume.startsWith(VOLUME_RAW_PREFIX, ignoreCase = true) ->
                volume.substring(VOLUME_RAW_PREFIX.length)

            volume.isBlank() -> null
            else -> "/storage/$volume"
        } ?: return null
        val path = if (relative.isBlank()) root else "$root/$relative"
        return path.trimEnd('/')
    }

    /** 文档 ID 的卷与相对路径分隔符数量。 */
    private const val LIMIT_TWO = 2

    /** 主共享存储卷前缀。 */
    private const val VOLUME_PRIMARY = "primary"

    /** 「已给真实路径」卷前缀。 */
    private const val VOLUME_RAW_PREFIX = "raw:"
}