// 文件：FileInfo.kt
// 职责：描述网盘上的单个文件/文件夹条目
// 依赖：无
// 协议：AGPL-3.0

package com.jieyundu.app.domain.model

/**
 * 网盘上的一个文件或文件夹。
 *
 * @property fid 文件 ID（各家网盘叫法不同，统一收敛到该字段）。
 * @property fileName 文件名（含扩展名）。
 * @property fileSize 文件大小，单位字节；文件夹为 0。
 * @property isDirectory 是否为文件夹。
 * @property downloadUrl 直链，可能为 null（需要单独调用接口换取，且有时效）。
 * @property shareFidToken 分享文件令牌；来自分享 `detail` 的每一条目，
 *   转存（`save`）时作为 `fid_token_list` 传入。个人网盘文件该字段为空串。
 */
data class FileInfo(
    val fid: String,
    val fileName: String,
    val fileSize: Long,
    val isDirectory: Boolean,
    val downloadUrl: String?,
    val shareFidToken: String = ""
) {
    /** 是否已经拿到可用直链。 */
    val isDirectLinkReady: Boolean
        get() = !downloadUrl.isNullOrBlank()
}