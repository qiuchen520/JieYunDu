// 文件：ShareTransfer.kt
// 职责：把夸克分享中的文件转存到本账号，返回转存后的新 fid（供取直链使用）
// 依赖：QuarkApi、QuarkFile、TaskPoller、TempFolderManager、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.transfer

import com.jieyundu.app.domain.parser.quark.QuarkApi
import com.jieyundu.app.domain.parser.quark.QuarkFile
import com.jieyundu.app.domain.parser.quark.QuarkSaveRequest
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * 夸克分享转存器。
 *
 * 为什么必须转存（《解析Bug分析.md》P0-1）：`file/download` 只认**自己网盘**里的文件，
 * 直接传分享 fid 拿不到直链。因此解析链路必须是：
 * `token → detail → save → poll → download(新 fid)`。
 *
 * 本批转存落在网盘根目录（不传 `to_pdir_fid`）；`.极云渡临时` 目录机制归阶段 13。
 *
 * @param api 夸克接口。
 * @param taskPoller 转存任务轮询器。
 * @param tempFolderManager 待清理 fid 登记器。
 */
@Singleton
class ShareTransfer @Inject constructor(
    private val api: QuarkApi,
    private val taskPoller: TaskPoller,
    private val tempFolderManager: TempFolderManager
) {

    /**
     * 转存指定分享文件并返回本账号中的新 fid。
     *
     * @param pwdId 分享 ID。
     * @param stoken 分享临时令牌。
     * @param files 待转存的分享文件（需含 `fid` 与 `share_fid_token`）。
     * @return 本账号中的新 fid 列表；失败返回空列表。
     */
    suspend fun saveAndCollectFids(
        pwdId: String,
        stoken: String,
        files: List<QuarkFile>
    ): List<String> {
        val fidList = files.map { file -> file.fid }
        val fidTokenList = files.map { file -> file.share_fid_token }
        val response = api.saveShare(
            QuarkSaveRequest(
                pwd_id = pwdId,
                stoken = stoken,
                fid_list = fidList,
                fid_token_list = fidTokenList,
                scene = SCENE_LINK
            )
        )
        if (response.code != SUCCESS_CODE || response.data.task_id.isBlank()) {
            Timber.e("ShareTransfer save failed code=%d", response.code)
            return emptyList()
        }
        val newFids = taskPoller.awaitSavedFids(response.data.task_id)
        tempFolderManager.recordPendingCleanup(newFids)
        return newFids
    }

    private companion object {
        /** 成功状态码（实测为 0）。 */
        const val SUCCESS_CODE = 0

        /** 转存场景：来自分享链接。 */
        const val SCENE_LINK = "link"
    }
}