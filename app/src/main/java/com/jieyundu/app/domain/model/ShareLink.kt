// 文件：ShareLink.kt
// 职责：描述从用户粘贴文本中提取出的分享链接（网盘类型 + 原始链接 + 分享 ID + 提取码）
// 依赖：NetdiskType
// 协议：AGPL-3.0

package com.jieyundu.app.domain.model

/**
 * 从一段自由文本中提取出来的分享链接。
 *
 * @property type 该链接所属的网盘类型。
 * @property rawUrl 原始分享链接（已去掉提取码查询串以外的空白字符）。
 * @property shareId 分享 ID，即链接中 `/s/` 之后的主体部分。
 * @property password 提取码，可能为 null（无需提取码或用户尚未提供）。
 */
data class ShareLink(
    val type: NetdiskType,
    val rawUrl: String,
    val shareId: String,
    val password: String? = null
)
