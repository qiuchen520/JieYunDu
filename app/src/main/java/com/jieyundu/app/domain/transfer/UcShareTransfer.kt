// 文件：UcShareTransfer.kt
// 职责：把 UC 分享中的文件转存到本账号临时目录，轮询取新 fid，并换取可下载直链（照夸克 ShareTransfer 同构）
// 依赖：UcApi、UcFile、UcTaskPoller、UcTempFolderManager、FileInfo、Timber
// 协议：AGPL-3.0
package com.jieyundu.app.domain.transfer

import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.parser.uc.UcApi
import com.jieyundu.app.domain.parser.uc.UcDownloadRequest
import com.jieyundu.app.domain.parser.uc.UcFile
import com.jieyundu.app.domain.parser.uc.UcSaveRequest
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException
import timber.log.Timber

/**
 * UC 分享转存器（照夸克 [ShareTransfer] 同构，域名/参数对齐《抓包事实.md》§2 / §6.1）。
 *
 * 为什么必须转存：`file/download` 只认**自己网盘**里的文件，直接传分享 fid 拿不到直链。
 * 因此任何分享文件的下载链路都是：`转存到临时目录 → 轮询取新 fid → file/download(新 fid)`。
 *
 * 转存目标：优先 `.极云渡临时` 目录；临时目录创建失败时回退根目录 `0`（可用性优先）。
 *
 * 取链注意：UC 直链 URL 带 `entry=ft`（见 [UcApi.getDownloadUrl]），且下载时须带 Referer
 * `https://drive.uc.cn/`（否则被限速到约 100KB/s；由 HomeViewModel 组装请求头）。
 *
 * @param api UC 接口。
 * @param taskPoller UC 转存任务轮询器。
 * @param tempFolderManager UC 临时目录管理器（查 / 建 / 登记 / 清理）。
 */
@Singleton
class UcShareTransfer @Inject constructor(
    private val api: UcApi,
    private val taskPoller: UcTaskPoller,
    private val tempFolderManager: UcTempFolderManager
) : ShareDownloadPreparer {
    override val type: NetdiskType = NetdiskType.UC

    /**
     * 转存指定分享文件并返回本账号中的新 fid。
     *
     * 字段语义（依《抓包事实.md》§9.3③「原样实录」）：
     * `pdir_fid` = **分享内的源目录**（[sourcePdirFid]），`to_pdir_fid` = 转存目标（[toPdirFid]）。
     * 此前两者都填了转存目标，与抓包不符 → 服务端按「转存文件 token 校验异常」拒绝（实测 403 / code 41020）。
     *
     * @param pwdId 分享 ID。
     * @param stoken 分享临时令牌。
     * @param files 待转存的分享文件（需含 `fid` 与 `share_fid_token`）。
     * @param sourcePdirFid 分享内源目录 fid（根为 `0`）。
     * @param toPdirFid 转存目标目录 fid（本账号）。
     * @return 本账号中的新 fid 列表；失败返回空列表。
     */
    suspend fun saveAndCollectFids(
        pwdId: String,
        stoken: String,
        files: List<UcFile>,
        sourcePdirFid: String,
        toPdirFid: String
    ): List<String> {
        val fidList = files.map { file -> file.fid }
        val fidTokenList = files.map { file -> file.share_fid_token }
        // 诊断（方案 A）：把实际将发出的关键字段写日志，便于核对 token 是否为空 / 目录 fid 是否正确。
        Timber.d(
            "UcShareTransfer save req pdir_fid=%s to_pdir_fid=%s fidCount=%d fidTokenCount=%d tokenBlank=%d",
            sourcePdirFid,
            toPdirFid,
            fidList.size,
            fidTokenList.size,
            fidTokenList.count { token -> token.isBlank() }
        )
        val response = callWithHttpLog(STEP_SAVE) {
            api.saveShare(
                UcSaveRequest(
                    pwd_id = pwdId,
                    stoken = stoken,
                    pdir_fid = sourcePdirFid,
                    to_pdir_fid = toPdirFid,
                    fid_list = fidList,
                    fid_token_list = fidTokenList,
                    scene = SCENE_LINK
                )
            )
        }
        val taskId = response.data?.task_id.orEmpty()
        if (response.code != SUCCESS_CODE || taskId.isBlank()) {
            Timber.e("UcShareTransfer save failed code=%d", response.code)
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
     * @param sourcePdirFid 分享内该文件所在目录的 fid（根为 `0`），作为 `save` 的 `pdir_fid`
     *   （见《抓包事实.md》§9.3③）。
     * @return 转存并取链结果；任一步失败返回 null。
     */
    override suspend fun prepare(
        pwdId: String,
        stoken: String,
        file: FileInfo,
        sourcePdirFid: String
    ): PreparedDownload? {
        val targetFid = tempFolderManager.ensureTempFolderFid() ?: ROOT_PDIR_FID.also {
            Timber.w("UcShareTransfer temp folder unavailable, fallback to root")
        }
        val newFids = saveAndCollectFids(
            pwdId = pwdId,
            stoken = stoken,
            files = listOf(file.toUcFile()),
            sourcePdirFid = sourcePdirFid,
            toPdirFid = targetFid
        )
        val newFid = newFids.firstOrNull()?.takeIf { fid -> fid.isNotBlank() } ?: return null
        val downloadResponse = callWithHttpLog(STEP_DOWNLOAD_URL) {
            api.getDownloadUrl(UcDownloadRequest(fids = listOf(newFid)))
        }
        if (downloadResponse.code != SUCCESS_CODE) {
            Timber.e("UcShareTransfer get download url failed code=%d", downloadResponse.code)
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
    private fun FileInfo.toUcFile(): UcFile = UcFile(
        fid = fid,
        file_name = fileName,
        size = fileSize,
        dir = isDirectory,
        share_fid_token = shareFidToken
    )

    /**
     * 调用 UC 接口，并在 HTTP 失败时把**服务端返回体**一并写入日志。
     *
     * 约束：异常**原样抛出**（C3，不吞不改）；响应体读取是阻塞 IO，调用方已运行在
     * [kotlinx.coroutines.Dispatchers.IO]（见 HomeViewModel.download），故不重复切换调度器。
     *
     * @param step 步骤名（写日志用，区分转存 / 取链）。
     * @param block 实际的接口调用。
     * @return 接口返回值。
     */
    private suspend fun <T> callWithHttpLog(step: String, block: suspend () -> T): T =
        try {
            block()
        } catch (http: HttpException) {
            val body = http.response()?.errorBody()?.string()?.take(MAX_ERROR_BODY_CHARS)
            Timber.e("UcShareTransfer %s HTTP %d body=%s", step, http.code(), body)
            throw http
        }

    private companion object {
        /** 成功状态码（实测为 0）。 */
        const val SUCCESS_CODE = 0

        /** 转存场景：来自分享链接。 */
        const val SCENE_LINK = "link"

        /** 转存目标回退值：根目录。 */
        const val ROOT_PDIR_FID = "0"

        /** 日志步骤名：转存（save）。 */
        const val STEP_SAVE = "save"

        /** 日志步骤名：取直链（file/download）。 */
        const val STEP_DOWNLOAD_URL = "download-url"

        /** 写入日志的响应体最大字符数（防止超长响应体淹没有用信息）。 */
        const val MAX_ERROR_BODY_CHARS = 500
    }
}