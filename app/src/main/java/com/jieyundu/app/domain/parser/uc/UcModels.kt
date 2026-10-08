// 文件：uc/UcModels.kt
// 职责：UC 接口的 wire 数据结构（请求体 / 响应体）+ 分享详情查询构造器 UcTransferDetailQuery
// 依赖：kotlinx.serialization
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.uc

import kotlinx.serialization.Serializable

// 【修订 JYD-DEBT3-2026-10-07】本文件自 UcApi.kt 拆出：接口只留端点，数据结构独立成文件。

/**
 * UC 接口的统一响应包装体（与夸克同构：成功 `code == 0`）。
 *
 * @property code 业务状态码；缺省按 -1 处理（未知错误）。
 * @property message 服务端描述。
 * @property data 业务数据；失败或该接口无返回体时为 null。
 */
@Serializable
data class UcResponse<T>(
    val code: Int = CODE_UNKNOWN,
    val message: String = "",
    val data: T? = null
) {
    companion object {
        /** 缺省（未提供）状态码。 */
        const val CODE_UNKNOWN = -1
    }
}

/**
 * stoken 响应体。
 *
 * @property stoken 临时令牌。
 * @property title 分享标题；缺省为空串。
 */
@Serializable
data class UcShareToken(
    val stoken: String,
    val title: String = ""
)

/**
 * 分享详情内层结构。
 *
 * @property list 条目列表。
 */
@Serializable
data class UcDetailInfo(
    val list: List<UcFile> = emptyList()
)

/**
 * `transfer_share/detail` 响应（UC 分享取链的 `share_fid_token` 来源）。
 *
 * 说明：官方未逐字给出包裹键，此处兼容三种常见形态（`Json` 已开 `ignoreUnknownKeys`，
 * 多键兼容不影响解析）：`detail_info.list` / `list` / `file_list`。
 *
 * @property detail_info 两级结构（兼容）。
 * @property list 扁平结构（兼容）。
 * @property file_list 文件列表结构（兼容）。
 */
@Serializable
data class UcTransferShareDetail(
    val detail_info: UcDetailInfo? = null,
    val list: List<UcFile> = emptyList(),
    val file_list: List<UcFile> = emptyList()
) {
    /** 条目列表：按 `detail_info.list` → `list` → `file_list` 取第一个非空。 */
    val entries: List<UcFile>
        get() = detail_info?.list?.takeIf { entries -> entries.isNotEmpty() }
            ?: list.takeIf { entries -> entries.isNotEmpty() }
            ?: file_list
}

/**
 * 分享 / 个人网盘内的单个条目。
 *
 * 字段与夸克一致：`fid/file_name/size/dir/share_fid_token`。
 *
 * @property fid 文件 ID。
 * @property file_name 文件名。
 * @property size 文件大小（字节）；文件夹为 0。
 * @property dir 是否为文件夹。
 * @property share_fid_token 分享文件令牌（转存 `fid_token_list` 需要）。
 */
@Serializable
data class UcFile(
    val fid: String,
    val file_name: String,
    val size: Long,
    val dir: Boolean = false,
    val share_fid_token: String = ""
)

/**
 * 转存响应体。
 *
 * @property task_id 异步任务 ID。
 */
@Serializable
data class UcSaveResult(
    val task_id: String = ""
)

/**
 * 取直链请求体（**分享直连取链**）。
 *
 * 依据：评审方《UC下载链路修正要点_交开发方.txt》/《评审清单.md》§13——UC 分享文件
 * **不需要先转存**，直接把分享 fid 与分享令牌传给 `file/download` 即可取链。字段名严格为
 * `fids` / `pwd_id` / `stoken` / `fids_token`（注意是 `fids_token`，**不是**转存 save 用的
 * `fid_token_list`）。
 *
 * @property fids 分享文件 ID 列表（字段名为 `fids`）。
 * @property pwd_id 分享 ID。
 * @property stoken 分享临时令牌（`token` 接口返回）。
 * @property fids_token 与 [fids] 一一对应的分享文件令牌 `share_fid_token`。
 */
@Serializable
data class UcDownloadRequest(
    val fids: List<String>,
    val pwd_id: String,
    val stoken: String,
    val fids_token: List<String>
)

/**
 * 下载直链条目。
 *
 * @property fid 文件 ID。
 * @property download_url 下载直链（UC 直链须带 Referer `https://drive.uc.cn/`）。
 */
@Serializable
data class UcDownloadUrl(
    val fid: String = "",
    val download_url: String = ""
)

/**
 * 个人网盘列表响应体。
 *
 * @property list 目录条目列表。
 */
@Serializable
data class UcFileList(
    val list: List<UcFile> = emptyList()
)

/**
 * 容量响应体（`member`）。
 *
 * 依据：《抓包事实.md》§10.1——字段与夸克一致。
 *
 * @property use_capacity 已用容量（字节）。
 * @property total_capacity 总容量（字节）。
 */
@Serializable
data class UcMember(
    val use_capacity: Long = 0L,
    val total_capacity: Long = 0L
)

/**
 * 删除（`file/delete`）请求体。
 *
 * 依据：《抓包事实.md》§10.3——与夸克同构。
 *
 * @property action_type 固定 2（移入回收站）。
 * @property filelist 待删除 fid 列表。
 * @property exclude_fids 排除列表；默认空。
 */
@Serializable
data class UcDeleteRequest(
    val action_type: Int = 2,
    val filelist: List<String>,
    val exclude_fids: List<String> = emptyList()
)

/**
 * UC `transfer_share/detail` 查询参数构造器。
 *
 * 存在理由（《参考实现_源码通读研究.md》§9.3①）：UC 分享的「文件列表」与「取链令牌
 * `share_fid_token`」**同源于本接口**——浏览列表与取链必须走同一个端点，否则两处拿到的
 * `fid` 可能对不上（列表用 `v2/detail` 的 fid 去 `transfer_share/detail` 里匹配令牌会落空，
 * 导致取不到令牌、下载被跳过）。为避免两处各写一套参数漂移，统一在此构造。
 *
 * 字段集严格对齐 §9.3①：
 * `pwd_id` / `pdir_fid` / `fetch_file_list=1` / `passcode` / `_page=1` / `_size=50`
 * / `_fetch_total=1` / `_fetch_task=1` / `_fetch_share=1` / `_sort=` / `stoken`。
 * （`entry=ft&fr=pc&pr=UCBrowser` 已固化在 [UcApi.transferShareDetail] 的路径上。）
 */
object UcTransferDetailQuery {

    /**
     * 构造 `transfer_share/detail` 查询参数。
     *
     * @param pwdId 分享 ID。
     * @param stoken 分享临时令牌。
     * @param pdirFid 目录 fid（根为 `0`）。
     * @param passcode 提取码；无则空串。
     * @return 查询参数键值对。
     */
    fun build(
        pwdId: String,
        stoken: String,
        pdirFid: String,
        passcode: String = ""
    ): Map<String, String> = linkedMapOf(
        KEY_PWD_ID to pwdId,
        KEY_PDIR_FID to pdirFid,
        KEY_FETCH_FILE_LIST to ONE_VALUE,
        KEY_PASSCODE to passcode,
        KEY_PAGE to ONE_VALUE,
        KEY_SIZE to PAGE_SIZE,
        KEY_FETCH_TOTAL to ONE_VALUE,
        KEY_FETCH_TASK to ONE_VALUE,
        KEY_FETCH_SHARE to ONE_VALUE,
        KEY_SORT to EMPTY_VALUE,
        KEY_STOKEN to stoken
    )

    /** 查询参数名与固定占位值。 */
    private const val KEY_PWD_ID = "pwd_id"
    private const val KEY_PDIR_FID = "pdir_fid"
    private const val KEY_FETCH_FILE_LIST = "fetch_file_list"
    private const val KEY_PASSCODE = "passcode"
    private const val KEY_PAGE = "_page"
    private const val KEY_SIZE = "_size"
    private const val KEY_FETCH_TOTAL = "_fetch_total"
    private const val KEY_FETCH_TASK = "_fetch_task"
    private const val KEY_FETCH_SHARE = "_fetch_share"
    private const val KEY_SORT = "_sort"
    private const val KEY_STOKEN = "stoken"
    private const val ONE_VALUE = "1"
    private const val PAGE_SIZE = "50"
    private const val EMPTY_VALUE = ""
}
