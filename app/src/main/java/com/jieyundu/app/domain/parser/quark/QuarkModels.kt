// 文件：quark/QuarkModels.kt
// 职责：夸克接口的 wire 数据结构（请求体 / 响应体），与 QuarkApi 的端点一一对应
// 依赖：kotlinx.serialization
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.quark

import kotlinx.serialization.Serializable

// 【修订 JYD-DEBT3-2026-10-07】本文件自 QuarkApi.kt 拆出：接口只留端点，数据结构独立成文件。

/**
 * 夸克接口的统一响应包装体。
 *
 * 实测：成功 `code == 0`；风控/业务失败携带非 0 业务码
 * （例：`31001` 表示 share missing / 需登录）。
 *
 * 说明（B1 修复）：`data` 改为**可空**。服务端在业务失败时会返回 `"data":`，
 * 若仍按非空解析会抛 `SerializationException`，把真实业务码 `code` 一并吞掉，
 * 使调用方只能得到笼统的"失败"。改为可空后，调用方可直接读取 `code` / `message`
 * 做出精确判定（下载失败不再显示为含义不明的异常）。
 *
 * @property code 业务状态码；缺省时按 -1 处理（视为未知错误）。
 * @property message 服务端描述。
 * @property data 业务数据；业务失败或该接口无返回体时为 null。
 */
@Serializable
data class QuarkResponse<T>(
    val code: Int = CODE_UNKNOWN,
    val message: String = "",
    val data: T? = null
) {
    companion object {
        /** 缺省（未提供）状态码，表示未知错误。 */
        const val CODE_UNKNOWN = -1
    }
}

/**
 * stoken 响应体。
 *
 * @property stoken 临时令牌。
 * @property title 分享标题；缺省时为空串（UI 侧以文件名兜底）。
 */
@Serializable
data class QuarkShareToken(
    val stoken: String,
    val title: String = ""
)

/**
 * 分享详情响应体。
 *
 * 真实结构为 `data.detail_info.list[]`；为兼容旧写法 `data.list[]`，两者都接收，
 * 通过 [entries] 统一取值（依据【修订 JYD-PARSE-2026-10-03】Q2）。
 *
 * @property detail_info 两级结构（真实）。
 * @property list 旧/扁平结构（兼容）。
 */
@Serializable
data class QuarkShareDetail(
    val detail_info: QuarkDetailInfo? = null,
    val list: List<QuarkFile> = emptyList()
) {
    /** 文件条目列表：优先取 `detail_info.list`，回退 `list`。 */
    val entries: List<QuarkFile>
        get() = detail_info?.list ?: list
}

/**
 * 分享详情的内层结构。
 *
 * @property list 文件条目列表。
 */
@Serializable
data class QuarkDetailInfo(
    val list: List<QuarkFile> = emptyList()
)

/**
 * 分享内的单个文件条目。
 *
 * 实测字段（节选）：`fid/file_name/size/dir/share_fid_token`。
 *
 * @property fid 文件 ID（分享内）。
 * @property file_name 文件名。
 * @property size 文件大小，单位字节；文件夹为 0。
 * @property dir 是否为文件夹（true 表示文件夹）。
 * @property share_fid_token 分享文件令牌；转存接口 `fid_token_list` 需要它。
 */
@Serializable
data class QuarkFile(
    val fid: String,
    val file_name: String,
    val size: Long,
    val dir: Boolean = false,
    val share_fid_token: String = ""
)

/**
 * 转存（save）响应体。
 *
 * @property task_id 异步转存任务 ID（需轮询 [QuarkApi.getTask]）。
 */
@Serializable
data class QuarkSaveResult(
    val task_id: String = ""
)

/**
 * 转存任务（task）响应体。
 *
 * @property status 任务状态码；2 表示完成。
 * @property finished_at 完成时间戳（秒）；>0 表示完成。
 * @property save_as 转存结果。
 */
@Serializable
data class QuarkTask(
    val status: Int = 0,
    val finished_at: Long = 0L,
    val save_as: QuarkSaveAs? = null
)

/**
 * 转存结果。
 *
 * @property save_as_top_fids 转存后在本账号中的**新 fid** 列表。
 */
@Serializable
data class QuarkSaveAs(
    val save_as_top_fids: List<String> = emptyList()
)

/**
 * 转存（save）请求体。
 *
 * 说明：此处用 `@Serializable` 请求模型而非 `Map<String, Any>`——本项目 Retrofit 采用
 * kotlinx.serialization 转换器，`Map<String, Any>` 因 `Any` 无序列化器会在运行期抛出
 * `SerializationException`，导致转存取链整条链路不可用。
 *
 * @property pwd_id 分享 ID。
 * @property stoken 分享临时令牌。
 * @property pdir_fid 转存目标目录 fid（根为 `0`，临时目录为 `.极云渡临时` 的 fid）。
 * @property to_pdir_fid 同 [pdir_fid]（接口要求两字段取值一致）。
 * @property fid_list 待转存的分享文件 fid 列表。
 * @property fid_token_list 与 [fid_list] 一一对应的 `share_fid_token` 列表。
 * @property scene 转存场景（固定 `link`，表示来自分享链接）。
 */
@Serializable
data class QuarkSaveRequest(
    val pwd_id: String,
    val stoken: String,
    val pdir_fid: String,
    val to_pdir_fid: String,
    val fid_list: List<String>,
    val fid_token_list: List<String>,
    val scene: String
)

/**
 * 取直链（file/download）请求体。
 *
 * @property fids 本账号中的文件 ID 列表（字段名固定为 `fids`）。
 */
@Serializable
data class QuarkDownloadRequest(
    val fids: List<String>
)

/**
 * 下载直链条目。
 *
 * 实测：`file/download` 的 `data[]` 是完整文件对象，
 * 每个条目同时含 `fid` 与 `download_url`，可按 `fid` 回填到对应文件。
 *
 * @property fid 文件 ID（本账号 fid）。
 * @property download_url 下载直链（指向 `dl-guest-*.drive.quark.cn`，须带 `__pugs` Cookie）。
 */
@Serializable
data class QuarkDownloadUrl(
    val fid: String = "",
    val download_url: String = ""
)

/**
 * 个人网盘文件列表响应体（`file/sort`）。
 *
 * @property list 目录条目列表。
 */
@Serializable
data class QuarkFileList(
    val list: List<QuarkFile> = emptyList()
)

/**
 * 个人网盘容量响应体（`member`）。
 *
 * 依据：《抓包事实.md》§10.1。字段保持服务端原始命名以便逐一核对。
 *
 * @property use_capacity 已用容量（字节）。
 * @property total_capacity 总容量（字节）。
 */
@Serializable
data class QuarkMember(
    val use_capacity: Long = 0L,
    val total_capacity: Long = 0L
)

/**
 * 创建目录（`file`）请求体。
 *
 * 依据：《抓包事实.md》§6.1⑥ / §9.1——夸克与 UC 请求体一致。
 *
 * @property pdir_fid 父目录 fid；根目录为 `0`。
 * @property file_name 新目录名。
 * @property dir_path 目录路径，实测固定空串。
 * @property dir_init_lock 目录初始化锁，实测固定 false。
 */
@Serializable
data class QuarkCreateFolderRequest(
    val pdir_fid: String,
    val file_name: String,
    val dir_path: String = "",
    val dir_init_lock: Boolean = false
)

/**
 * 创建目录响应体。
 *
 * @property fid 新建目录的 fid。
 */
@Serializable
data class QuarkCreateFolderResult(
    val fid: String = ""
)

/**
 * 删除（`file/delete`）请求体。
 *
 * @property action_type 操作类型；2 = 移入回收站。
 * @property filelist 待删除的 fid 列表。
 * @property exclude_fids 排除的 fid（固定空）。
 */
@Serializable
data class QuarkDeleteRequest(
    val action_type: Int = ACTION_TYPE_TRASH,
    val filelist: List<String>,
    val exclude_fids: List<String> = emptyList()
) {
    companion object {
        /** `action_type`：移入回收站。 */
        const val ACTION_TYPE_TRASH = 2
    }
}

/**
 * 删除响应体。
 *
 * @property task_id 异步删除任务 ID。
 */
@Serializable
data class QuarkDeleteResult(
    val task_id: String = ""
)
