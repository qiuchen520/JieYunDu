// 文件：UcShareTransfer.kt
// 职责：把 UC 分享文件**直接**换取可下载直链（不转存、不轮询），供下载引擎使用
// 依赖：UcApi、UcDownloadRequest、FileInfo、Timber
// 协议：AGPL-3.0
package com.jieyundu.app.domain.transfer

import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.parser.uc.UcApi
import com.jieyundu.app.domain.parser.uc.UcDownloadRequest
import com.jieyundu.app.domain.parser.uc.UcTransferDetailQuery
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException
import timber.log.Timber

/**
 * UC 分享取链器。
 *
 * 关键事实（评审方《UC下载链路修正要点_交开发方.txt》+《UC取链请求_逐字段对照.txt》）：
 * UC 分享文件**不需要先转存**。正确链路为两步：
 *
 * 1. **取 token**：`GET /1/clouddrive/transfer_share/detail?entry=ft&fr=pc&pr=UCBrowser`
 *    （带 `pwd_id` / `pdir_fid` / `stoken`），从响应列表项的 `share_fid_token` 取到与本次
 *    stoken 绑定的下载令牌。**不是** `v2/detail` 返回的令牌——用它会 `41020 token 校验异常`。
 * 2. **取直链**：`POST /1/clouddrive/file/download?entry=ft&fr=pc&pr=UCBrowser`，body：
 *    ```
 *    {"fids":["<分享fid>"],"pwd_id":"<shareId>",
 *     "stoken":"<token接口返回的stoken>","fids_token":["<transfer_share/detail的share_fid_token>"]}
 *    ```
 * 成功判定 `status == 200 && code == 0`，取 `data[0].download_url`。下载字节时须带 Referer
 * `https://drive.uc.cn/`（由 HomeViewModel 组装请求头），否则会被限流。
 *
 * 注意：本改造**只动 UC**，夸克仍走 [ShareTransfer] 的转存链路，不受影响。
 *
 * @param api UC 接口。
 */
@Singleton
class UcShareTransfer @Inject constructor(
    private val api: UcApi
) : ShareDownloadPreparer {

    override val type: NetdiskType = NetdiskType.UC

    /**
     * 用分享 fid 取下载直链（不转存）。
     *
     * @param pwdId 分享 ID。
     * @param stoken 分享临时令牌。
     * @param file 待下载的分享文件（需含 `fid`）。
     * @param sourcePdirFid 分享内该文件所在目录的 fid（根为 `0`），作为 `transfer_share/detail`
     *   的 `pdir_fid`。
     * @return 取链结果；任一步失败返回 null。
     */
    override suspend fun prepare(
        pwdId: String,
        stoken: String,
        file: FileInfo,
        sourcePdirFid: String
    ): PreparedDownload? {
        val fidToken = fetchShareFidToken(pwdId, stoken, sourcePdirFid, file.fid)
        if (fidToken.isNullOrBlank()) {
            Timber.e("UcShareTransfer share fid token unavailable fid=%s", file.fid)
            return null
        }
        val response = callWithHttpLog(STEP_DOWNLOAD_URL) {
            api.getDownloadUrl(
                UcDownloadRequest(
                    fids = listOf(file.fid),
                    pwd_id = pwdId,
                    stoken = stoken,
                    fids_token = listOf(fidToken)
                )
            )
        }
        if (response.code != SUCCESS_CODE) {
            Timber.e("UcShareTransfer get download url failed code=%d", response.code)
            return null
        }
        val items = response.data.orEmpty()
        val url = items
            .firstOrNull { item -> item.fid == file.fid }
            ?.download_url
            ?.takeIf { value -> value.isNotBlank() }
            ?: items.firstOrNull()
                ?.download_url
                ?.takeIf { value -> value.isNotBlank() }
            ?: return null
        // 直连取链无转存副本，newFid 仅作稳定标识回传（分享 fid），不用于清理。
        return PreparedDownload(newFid = file.fid, url = url)
    }

    /**
     * 从 `transfer_share/detail` 取与本次 stoken 绑定的下载令牌。
     *
     * @param pwdId 分享 ID。
     * @param stoken 分享临时令牌。
     * @param pdirFid 分享内目录 fid（根为 `0`）。
     * @param fid 目标分享文件 fid。
     * @return `share_fid_token`；失败返回 null。
     */
    private suspend fun fetchShareFidToken(
        pwdId: String,
        stoken: String,
        pdirFid: String,
        fid: String
    ): String? {
        val response = callWithHttpLog(STEP_TRANSFER_DETAIL) {
            api.transferShareDetail(UcTransferDetailQuery.build(pwdId, stoken, pdirFid))
        }
        if (response.code != SUCCESS_CODE) {
            Timber.e("UcShareTransfer transfer_share/detail failed code=%d", response.code)
            return null
        }
        val entries = response.data?.entries.orEmpty()
        Timber.d("UcShareTransfer transfer-detail entries=%d target=%s", entries.size, fid)
        return entries.firstOrNull { entry -> entry.fid == fid }?.share_fid_token
    }

    /**
     * UC 直连取链**不产生转存副本**，故清理为空操作。
     *
     * @param newFid [prepare] 返回的分享 fid（仅作占位，无实际清理语义）。
     */
    override suspend fun cleanupAfterDownload(newFid: String) {
        // 直连取链无转存副本，无需清理（保留接口以满足 ShareDownloadPreparer 契约）。
    }

    /**
     * 调用 UC 接口，并在 HTTP 失败时把**服务端返回体**一并写入日志。
     *
     * 约束：异常**原样抛出**（C3，不吞不改）；响应体读取是阻塞 IO，调用方已运行在
     * [kotlinx.coroutines.Dispatchers.IO]（见 HomeViewModel.download），故不重复切换调度器。
     *
     * @param step 步骤名（写日志用）。
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

        /** 日志步骤名：取链令牌（transfer_share/detail）。 */
        const val STEP_TRANSFER_DETAIL = "transfer-detail"

        /** 日志步骤名：取直链（file/download）。 */
        const val STEP_DOWNLOAD_URL = "download-url"

        /** 写入日志的响应体最大字符数（防止超长响应体淹没有用信息）。 */
        const val MAX_ERROR_BODY_CHARS = 500
    }
}