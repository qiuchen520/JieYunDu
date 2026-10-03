// 文件：ShareDownloadPreparer.kt
// 职责：定义「把分享文件转存到本账号临时目录并换取可下载直链」的统一能力接口
// 依赖：FileInfo、NetdiskType
// 协议：AGPL-3.0

package com.jieyundu.app.domain.transfer

import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType

/**
 * 转存并换取直链的结果。
 *
 * @property newFid 转存后在本账号中的新 fid（下载完成后据此清理临时文件）。
 * @property url 可下载直链（有时效，须尽快使用）。
 */
data class PreparedDownload(
    val newFid: String,
    val url: String
)

/**
 * 分享文件下载准备器统一接口。
 *
 * 存在理由（《解析Bug分析.md》P0-1）：`file/download` 只认自己网盘里的文件，
 * 因此任何分享文件的下载都必须先「转存到本账号 → 轮询取新 fid → 换直链」。
 * 本接口把这套链路封装为一个动作，调用方（UI）只需给出分享文件与上下文。
 *
 * 转存目标为本账号的临时目录（`.极云渡临时`）；临时目录不存在时由实现方按需创建，
 * 创建失败则回退到根目录（保证可用性优先）。
 */
interface ShareDownloadPreparer {

    /** 本实现负责的网盘类型。 */
    val type: NetdiskType

    /**
     * 转存指定分享文件并换取直链。
     *
     * @param pwdId 分享 ID。
     * @param stoken 分享临时令牌。
     * @param file 待下载的分享文件（需含 `fid` 与 `shareFidToken`）。
     * @param sourcePdirFid **分享内**该文件所在目录的 fid（根为 `0`）。
     *   依《抓包事实.md》§9.3③，`save` 的 `pdir_fid` 指**源目录**、`to_pdir_fid` 指转存目标，
     *   二者不是同一个值；故调用方需把「当前浏览层级」的目录 fid 传进来。
     * @return 转存并取链结果；任一步失败返回 null。
     */
    suspend fun prepare(
        pwdId: String,
        stoken: String,
        file: FileInfo,
        sourcePdirFid: String
    ): PreparedDownload?

    /**
     * 下载完成后清理该文件的转存副本（失败时调用方不应调用，以便保留续传）。
     *
     * @param newFid [prepare] 返回的本账号新 fid。
     */
    suspend fun cleanupAfterDownload(newFid: String)
}
