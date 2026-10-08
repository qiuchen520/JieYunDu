// 文件：XunleiModels.kt
// 职责：迅雷接口的 wire 数据结构（分享解析 / 子目录 / 配额），字段严格取自抓包事实
// 依赖：kotlinx.serialization
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.xunlei

import kotlinx.serialization.Serializable

/**
 * 迅雷接口的 wire 结构（【JYD-XUNLEI-P1A-2026-10-08】）。
 *
 * ⚠️ 字段纪律（铁律 R3）：只声明《抓包事实.md》**已证实**的字段；未证实的一律
 * `TODO(用户抓包)` 标注并给安全默认值，绝不臆造字段名。
 *
 * 事实来源：§11.4 #10（分享解析）、#11（分享子目录）、#14（配额）。
 */

/**
 * 分享解析响应（§11.4 #10）。
 *
 * @property title 分享标题。
 * @property files 该层文件列表。
 * @property pass_code_token 提取码令牌（展开子目录时作为 `pass_code_token` 回传）。
 * @property next_page_token 翻页游标；为空表示没有下一页。
 * @property share_status 分享状态：`PASS_CODE_EMPTY` / `PASS_CODE_ERROR` / `PASS_CODE_NEED`。
 * @property error 服务端错误标识（失败时透传，便于定位；字段名见 §11.4 通用错误约定）。
 * @property error_description 服务端错误描述（失败时透传给用户）。
 */
@Serializable
data class XunleiShareResponse(
    val title: String? = null,
    val files: List<XunleiShareFile> = emptyList(),
    val pass_code_token: String? = null,
    val next_page_token: String? = null,
    val share_status: String? = null,
    val error: String? = null,
    val error_description: String? = null
)

/**
 * 分享子目录响应（§11.4 #11）。
 *
 * @property files 该目录条目。
 * @property next_page_token 翻页游标。
 * @property error 服务端错误标识。
 * @property error_description 服务端错误描述。
 */
@Serializable
data class XunleiShareDetailResponse(
    val files: List<XunleiShareFile> = emptyList(),
    val next_page_token: String? = null,
    val error: String? = null,
    val error_description: String? = null
)

/**
 * 分享内单个条目。
 *
 * 字段依据：
 * - `id`：§11.4 #12 转存用 `file_ids`、#9 取链用 `files/{fileId}` → 条目 id 存在且用于这两处；
 * - `name`：§11.4 #8 建目录请求体 `name` 证实同名概念；
 * - `kind`：§11.4 #8 请求体 `kind: "drive#folder"` 证实目录标记取值。
 *
 * ⚠️ `TODO(用户抓包)`：条目**其余字段**（大小 / 时间 / 是否目录的其它表达）在事实文档中
 * 没有逐字段列出。故：
 * - [size] 字段名未证实——若服务端不返回该名，解析结果为 0（UI 会显示 0 B），
 *   拿到分享报文后按实测字段名修正；
 * - [isDirectory] 仅以**已证实**的 `kind == "drive#folder"` 判定，不猜测文件侧取值。
 *
 * @property id 条目 id（目录展开与转存都用它）。
 * @property name 条目名称。
 * @property kind 条目类型标识；目录为 `drive#folder`。
 * @property size 文件大小（字节）；字段名待抓包确认，缺失时为 0。
 */
@Serializable
data class XunleiShareFile(
    val id: String = "",
    val name: String = "",
    val kind: String? = null,
    val size: Long = 0
) {

    /** 是否为目录（按已证实的 `kind` 取值判定）。 */
    val isDirectory: Boolean
        get() = kind == XunleiConfig.KIND_FOLDER
}

/**
 * 容量响应（§11.4 #14：`data.quota.limit / usage / usage_in_trash`）。
 *
 * 本批仅落地数据结构（个人网盘管理属阶段 3），解析保持宽松。
 *
 * @property data 容量数据体。
 */
@Serializable
data class XunleiAboutResponse(
    val data: XunleiAboutData? = null
)

/**
 * 容量数据体（§11.4 #14）。
 *
 * @property quota 配额明细。
 */
@Serializable
data class XunleiAboutData(
    val quota: XunleiQuota? = null
)

/**
 * 配额明细（§11.4 #14）。
 *
 * @property limit 总量（字节）。
 * @property usage 已用（字节）。
 * @property usage_in_trash 回收站占用（字节）。
 */
@Serializable
data class XunleiQuota(
    val limit: Long = 0,
    val usage: Long = 0,
    val usage_in_trash: Long = 0
)
