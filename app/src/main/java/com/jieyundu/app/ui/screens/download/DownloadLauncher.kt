// 文件：DownloadLauncher.kt
// 职责：下载投递的共享入口——落盘目录解析 / 建任务 / 投递引擎与前台服务 / 等待完成 / 发布成品
// 依赖：DownloadEngine、AppSettingsStore、DownloadSessionRegistry、PublicDownloadsPublisher、DownloadService
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.download

import android.content.Context
import android.os.Environment
import com.jieyundu.app.data.settings.AppSettingsStore
import com.jieyundu.app.data.settings.DownloadDirectoryMode
import com.jieyundu.app.data.storage.PublicDownloadsPublisher
import com.jieyundu.app.domain.downloader.DownloadEngine
import com.jieyundu.app.domain.downloader.DownloadState
import com.jieyundu.app.domain.downloader.DownloadTask
import com.jieyundu.app.service.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

/**
 * 下载投递的**共享入口**（【JYD-DEBT1-2026-10-05】去重）。
 *
 * 存在理由：首页下载（HomeViewModel）与网盘管理页下载（NetdiskBrowserViewModel）原本
 * 各自持有一份「解析落盘目录 → 建任务 → 登记会话 → 投递引擎 → 启动前台服务 → 等待完成
 * → 发布到公共目录」的实现（约 70 行 × 2，且已出现细微漂移风险）。本类把这条链路收敛为
 * **单一实现**，两个入口只表达自己的差异（URL / 文件名 / 大小 / 请求头）。
 *
 * 分层说明：本类位于 UI 层（`ui/screens/download`），因为 [DownloadSessionRegistry] 属 UI 侧
 * 会话登记；它只调用 domain（引擎、设置端口）与 data（发布器）能力，不反向被其依赖。
 */
@Singleton
class DownloadLauncher @Inject constructor(
    private val downloadEngine: DownloadEngine,
    private val appSettingsStore: AppSettingsStore,
    private val downloadSessionRegistry: DownloadSessionRegistry,
    private val publicDownloadsPublisher: PublicDownloadsPublisher,
    @ApplicationContext private val appContext: Context
) {

    /**
     * 一次已投递的下载。
     *
     * @property taskId 任务 ID（下载列表据此关联）。
     * @property workingFile 工作目录中的成品文件（发布前的位置）。
     */
    data class Launched(
        val taskId: String,
        val workingFile: File
    )

    /**
     * 投递一次下载：建任务 → 登记会话 → 交引擎 → 启动前台服务。
     *
     * 说明：前台服务只承担保活与通知，启动失败不影响下载本身（见 `DownloadService.start`）。
     *
     * @param url 下载直链。
     * @param fileName 文件名（同时作为任务名持久化）。
     * @param fileSize 文件大小；未知传 -1（退化为单分片开放式下载）。
     * @param headers 额外请求头（网盘要求的 Cookie / Referer 等）。
     * @return 已投递的任务信息。
     */
    suspend fun start(
        url: String,
        fileName: String,
        fileSize: Long,
        headers: Map<String, String> = emptyMap()
    ): Launched {
        val taskId = UUID.randomUUID().toString()
        val directory = resolveDownloadDirectory()
        val targetFile = File(directory, fileName)
        val task = DownloadTask(
            taskId = taskId,
            url = url,
            fileName = fileName,
            fileSize = fileSize,
            savePath = targetFile.absolutePath,
            chunkCount = appSettingsStore.chunkCount.value,
            headers = headers
        )
        downloadSessionRegistry.remember(taskId, fileName, task.savePath)
        downloadEngine.start(task)
        DownloadService.start(appContext, task)
        return Launched(taskId = taskId, workingFile = targetFile)
    }

    /**
     * 等待任务进入终态。
     *
     * @param taskId 任务 ID。
     * @return true 表示下载完成。
     */
    suspend fun awaitCompleted(taskId: String): Boolean {
        val finalState = downloadEngine.observe(taskId).firstOrNull { state ->
            state == DownloadState.COMPLETED ||
                state == DownloadState.FAILED ||
                state == DownloadState.CANCELED
        }
        return finalState == DownloadState.COMPLETED
    }

    /**
     * 默认目录模式下，把成品发布到公共下载目录（A3），并更新会话登记为已发布位置。
     *
     * @param taskId 任务 ID。
     * @param fileName 文件名。
     * @param workingFile 工作目录中的成品。
     */
    suspend fun publishIfNeeded(taskId: String, fileName: String, workingFile: File) {
        if (appSettingsStore.downloadDirectoryMode != DownloadDirectoryMode.PUBLIC_DOWNLOADS) {
            return
        }
        val published = publicDownloadsPublisher.publish(
            workingFile,
            appSettingsStore.publicFolderName()
        )
        if (published == null) {
            Timber.w("DownloadLauncher: publish to public downloads failed for %s", fileName)
            return
        }
        downloadSessionRegistry.remember(taskId, fileName, published)
        if (workingFile.isFile && !workingFile.delete()) {
            Timber.e("DownloadLauncher: failed to delete private copy: %s", workingFile.name)
        }
    }

    /**
     * 计算本次下载的工作目录（B2 功能①）。
     *
     * 规则：
     * - CUSTOM 模式且已设置有效路径 → 直接写入用户目录（需「所有文件访问」A1 权限）；
     * - 否则（默认 PUBLIC_DOWNLOADS）→ 先写应用私有下载目录，完成后再发布到公共目录（A3）。
     *
     * @return 工作目录；自定义目录不可用时回退应用私有目录。
     */
    private fun resolveDownloadDirectory(): File {
        if (appSettingsStore.downloadDirectoryMode == DownloadDirectoryMode.CUSTOM) {
            val custom = appSettingsStore.customDirectoryPath
            if (!custom.isNullOrBlank()) {
                val dir = File(custom)
                if (dir.isDirectory || dir.mkdirs()) {
                    return dir
                }
                Timber.w("DownloadLauncher: custom dir unavailable, fall back to private: %s", custom)
            }
        }
        return appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
    }
}
