// 文件：ParseResult.kt
// 职责：定义网盘解析的统一结果类型（成功 / 需要提取码 / 失败）
// 依赖：NetdiskType、FileInfo
// 协议：AGPL-3.0

package com.jieyundu.app.domain.model

/**
 * 分享链接的解析结果。
 *
 * 三个子类覆盖全部场景，调用方用 `when` 穷举处理，避免出现未定义状态。
 */
sealed class ParseResult {

    /**
     * 解析成功。
     *
     * @property netdiskType 所属网盘类型。
     * @property shareTitle 分享标题（没有标题时返回空串或文件名本身）。
     * @property files 分享内的文件列表。
     */
    data class Success(
        val netdiskType: NetdiskType,
        val shareTitle: String,
        val files: List<FileInfo>
    ) : ParseResult()

    /**
     * 该分享需要提取码。
     *
     * @property netdiskType 所属网盘类型。
     */
    data class NeedPassword(
        val netdiskType: NetdiskType
    ) : ParseResult()

    /**
     * 解析失败。
     *
     * @property netdiskType 所属网盘类型。
     * @property code 业务错误码（网盘返回的 code，或本模块自定义码）。
     * @property message 可直接展示给用户的错误描述。
     */
    data class Error(
        val netdiskType: NetdiskType,
        val code: String,
        val message: String
    ) : ParseResult()
}
