// 文件：ShareTransfer.kt
// 职责：把夸克分享中的文件转存到本账号临时目录，轮询取新 fid，并换取可下载直链
// 依赖：QuarkApi、QuarkFile、TaskPoller、TempFolderManager、FileInfo、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.transfer

import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.parser.quark.QuarkApi
import com.jieyundu.app.domain.parser.quark.QuarkDownloadRequest
import com.jieyundu.app.domain.parser.quark.QuarkFile
import com.jieyundu.app.domain.parser.quark.QuarkSaveRequest
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * 夸克分享转存器。
 *
 * 为什么必须转存（《解析Bug分析.md》P0-1）：`file/download` 只认**自己网盘**里的文件，
 * 直接传分享 fid 拿不到直链。因此任何分享文件的下载链路都是：
 * `转存到临时目录 → 轮询取新 fid → file/download(新 fid)`。
 *
 * 转存目标（阶段 13：【修订 JYD-TEMP-2026-10-03】）：优先 `.极云渡临时` 目录；
 * 临时目录创建失败时回退根目录 `0`（可用性优先），并在日志中告警。
 *
 * @param api 夸克接口。
 * @param taskPoller 转存任务轮询器。
 * @param tempFolderManager 临时目录管理器（查 / 建 / 登记 / 清理）。
 */
@Singleton
class ShareTransfer @Inject constructor(
    private val api: QuarkApi,
    private val taskPoller: TaskPoller,
    private val tempFolderManager: TempFolderManager
) : ShareDownloadPreparer {

    override val type: NetdiskType = NetdiskType.QUARK

    /**
     * 转存指定分享文件并返回本账号中的新 fid。
     *
     * @param pwdId 分享 ID。
     * @param stoken 分享临时令牌。
     * @param files 待转存的分享文件（需含 `fid` 与 `share_fid_token`）。
     * @param toPdirFid 转存目标目录 fid。
     * @return 本账号中的新 fid 列表；失败返回空列表。
     */
    suspend fun saveAndCollectFids(
        pwdId: String,
        stoken: String,
        files: List<QuarkFile>,
        toPdirFid: String
    ): List<String> {
        val fidList = files.map { file -> file.fid }
        val fidTokenList = files.map { file -> file.share_fid_token }
        val response = api.saveShare(
            QuarkSaveRequest(
                pwd_id = pwdId,
                stoken = stoken,
                pdir_fid = toPdirFid,
                to_pdir_fid = toPdirFid,
                fid_list = fidList,
                fid_token_list = fidTokenList,
                scene = SCENE_LINK
            )
        )
        val taskId = response.data?.task_id.orEmpty()
        if (response.code != SUCCESS_CODE || taskId.isBlank()) {
            Timber.e("ShareTransfer save failed code=%d", response.code)
            return emptyList()
        }
        val newFids = taskPoller.awaitSavedFids(taskId)
        tempFolderManager.recordPendingCleanup(newFids)
        return newFids
    }

    /**
     * 转存单个分享文件到临时目录、轮询取新 fid，并换取直链。
     *
     * @param pwdId 分享 ID。
     * @param stoken 分享临时令牌。
     * @param file 待下载的分享文件（需含 `fid` 与 `shareFidToken`）。
     * @return 转存并取链结果；任一步失败返回 null。
     */
    override suspend fun prepare(
        pwdId: String,
        stoken: String,
        file: FileInfo
    ): PreparedDownload? {
        val targetFid = tempFolderManager.ensureTempFolderFid() ?: ROOT_PDIR_FID.also {
            Timber.w("ShareTransfer temp folder unavailable, fallback to root")
        }
        val newFids = saveAndCollectFids(
            pwdId = pwdId,
            stoken = stoken,
            files = listOf(file.toQuarkFile()),
            toPdirFid = targetFid
        )
        val newFid = newFids.firstOrNull()?.takeIf { fid -> fid.isNotBlank() } ?: return null

        val downloadResponse = api.getDownloadUrl(QuarkDownloadRequest(fids = listOf(newFid)))
        if (downloadResponse.code != SUCCESS_CODE) {
            Timber.e("ShareTransfer get download url failed code=%d", downloadResponse.code)
            return null
        }
        val items = downloadResponse.data.orEmpty()
        val url = items
            .firstOrNull { item -> item.fid == newFid }
            ?.download_url
            ?.takeIf { value -> value.isNotBlank() }
            ?: items.firstOrNull()
                ?.download_url
                ?.takeIf { value -> value.isNotBlank() }
            ?: return null
        return PreparedDownload(newFid = newFid, url = url)
    }

    /**
     * 下载完成后删除该文件的转存副本；临时目录空了则一并删除。
     *
     * @param newFid [prepare] 返回的本账号新 fid。
     */
    override suspend fun cleanupAfterDownload(newFid: String) {
        tempFolderManager.deleteFromTemp(newFid)
    }

    /**
     * 把领域模型映射回转存接口所需的分享文件模型。
     *
     * @return 分享文件条目（含 `share_fid_token`）。
     */
    private fun FileInfo.toQuarkFile(): QuarkFile = QuarkFile(
        fid = fid,
        file_name = fileName,
        size = fileSize,
        dir = isDirectory,
        share_fid_token = shareFidToken
    )

    private companion object {
        /** 成功状态码（实测为 0）。 */
        const val SUCCESS_CODE = 0

        /** 转存场景：来自分享链接。 */
        const val SCENE_LINK = "link"

        /** 转存目标回退值：根目录。 */
        const val ROOT_PDIR_FID = "0"
    }
}