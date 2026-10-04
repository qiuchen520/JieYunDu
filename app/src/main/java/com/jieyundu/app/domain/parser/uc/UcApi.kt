// 文件：UcApi.kt
// 职责：UC 网盘分享解析的 Retrofit 接口定义与响应体结构（参数/字段对齐《抓包事实.md》§2/§6.1）
// 依赖：Retrofit、kotlinx.serialization
// 协议：AGPL-3.0
package com.jieyundu.app.domain.parser.uc

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.QueryMap

/**
 * UC 网盘解析接口（`pc-api.uc.cn`）。
 *
 * 接口契约来源：《抓包事实.md》§2「UC —— 与夸克同构，域名/参数/UA 全不同」与 §6.1
 * 「夸克 / UC 共用结构」。UC 与夸克同源，链路一致（token → detail → save → task →
 * file/download），但**域名、平台参数（`pr=UCBrowser`）、列表走 `v2/detail`、
 * 取链带 `entry=ft`** 均与夸克不同，故单独定义、不共用夸克接口。
 *
 * @see com.jieyundu.app.domain.parser.quark.QuarkApi 夸克同构参照
 */
interface UcApi {

    /**
     * 换取分享 stoken（临时令牌）。
     *
     * 请求：`POST https://pc-api.uc.cn/1/clouddrive/share/sharepage/token?pr=UCBrowser&fr=pc`
     * 请求体：`{"pwd_id":"<shareId>","share_for_transfer":true}`；带提取码时追加 `"passcode"`。
     * 响应：`data.stoken`、`data.title`。
     *
     * 依据：《抓包事实.md》§6.1①——**UC 的字段是 `share_for_transfer`，与夸克的
     * `support_visit_limit_private_share` 不同，不可抄混**。
     *
     * @param body 请求体键值对。
     * @return 统一响应包装体。
     */
    @POST("1/clouddrive/share/sharepage/token?pr=UCBrowser&fr=pc")
    suspend fun getShareToken(
        @Body body: Map<String, String>
    ): UcResponse<UcShareToken>

    /**
     * 列出分享内指定目录的条目（UC 为 `v2/detail`，用 POST + 请求体）。
     *
     * 请求：`POST https://pc-api.uc.cn/1/clouddrive/share/sharepage/v2/detail?pr=UCBrowser&fr=pc&ve=2.5.20`
     * （`ve=2.5.20` 依《抓包事实.md》§9.3②「原样实录」补入，与游客 UA `uc-cloud-drive/2.5.20` 一致。）
     * 请求体见 [UcShareDetailRequest]。
     * 响应：优先 `data.detail_info.list[]`，兼容 `data.list[]`。
     *
     * 依据：《抓包事实.md》§2 与 §6.1②。
     *
     * @param body 请求体。
     * @return 统一响应包装体。
     */
    @POST("1/clouddrive/share/sharepage/v2/detail?pr=UCBrowser&fr=pc&ve=2.5.20")
    suspend fun getShareDetail(
        @Body body: UcShareDetailRequest
    ): UcResponse<UcShareDetail>

    /**
     * 取「转存详情」列表（**带 stoken**）——UC 分享取链所需的 `share_fid_token` 来源。
     *
     * 请求：`GET https://pc-api.uc.cn/1/clouddrive/transfer_share/detail?entry=ft&fr=pc&pr=UCBrowser`
     *   + `pwd_id` / `pdir_fid` / `fetch_file_list=1` / `passcode=` / `_page` / `_size`
     *   / `_fetch_total` / `_fetch_task` / `_fetch_share` / `_sort` / `stoken`。
     * 额外请求头：`Origin: https://fast.uc.cn`、`Referer: https://fast.uc.cn/`。
     * 响应：`data` 内列表项含 `share_fid_token`。
     *
     * 依据：评审方《UC取链请求_逐字段对照.txt》§三——UC 官方下载流程实际用的是**本接口**
     * （**不是** `v2/detail`）；其返回的 `share_fid_token` 与本次 stoken 绑定，只有它才能
     * 通过 `file/download` 的 token 校验（用 `v2/detail` 的令牌会 `41020 token 校验异常`）。
     *
     * @param params 查询参数键值对。
     * @return 统一响应包装体。
     */
    @Headers("Origin: https://fast.uc.cn", "Referer: https://fast.uc.cn/")
    @GET("1/clouddrive/transfer_share/detail?entry=ft&fr=pc&pr=UCBrowser")
    suspend fun transferShareDetail(
        @QueryMap params: Map<String, String>
    ): UcResponse<UcTransferShareDetail>

    /**
     * 把分享中的文件转存到本账号（save）。
     *
     * 请求：`POST https://pc-api.uc.cn/1/clouddrive/share/sharepage/save?pr=UCBrowser&fr=pc`
     * 响应：`data.task_id`。
     *
     * ⚠️ 字段语义（依《抓包事实.md》§9.3③「原样实录」，**与 §6.1③ 的旧描述冲突，以 §9.3 为准**）：
     * - `pdir_fid` = **分享内的源目录** fid（根为 `0`）；
     * - `to_pdir_fid` = **转存目标**（本账号）目录 fid。
     * 二者**不是同一个值**（§6.1③ 旧文误写为「目标目录，根为 0」且两者相同）。
     *
     * 依据：《抓包事实.md》§2 与 §6.1③（请求体字段集），字段语义见 §9.3③。
     *
     * @param body 请求体。
     * @return 统一响应包装体，data 含 task_id。
     */
    @POST("1/clouddrive/share/sharepage/save?pr=UCBrowser&fr=pc")
    suspend fun saveShare(
        @Body body: UcSaveRequest
    ): UcResponse<UcSaveResult>

    /**
     * 轮询转存任务状态（task）。
     *
     * 请求：`GET https://pc-api.uc.cn/1/clouddrive/task?pr=UCBrowser&fr=pc&task_id=...`
     * 响应：`data.finished_at` / `data.status` / `data.save_as.save_as_top_fids[]`。
     *
     * @param params 查询参数键值对。
     * @return 统一响应包装体。
     */
    @GET("1/clouddrive/task")
    suspend fun getTask(
        @QueryMap params: Map<String, String>
    ): UcResponse<UcTask>

    /**
     * 取下载直链（**分享直连取链，无需先转存**）。
     *
     * 请求：`POST https://pc-api.uc.cn/1/clouddrive/file/download?entry=ft&fr=pc&pr=UCBrowser`
     * 请求体见 [UcDownloadRequest]（`fids` + `pwd_id` + `stoken` + `fids_token`）。
     * 响应：`data[]`（含 `fid` 与 `download_url`）。
     *
     * 依据：评审方《UC下载链路修正要点_交开发方.txt》/《评审清单.md》§13——UC 分享文件
     * **不需要先转存**，直接把分享 fid 与分享令牌传给本接口即可取链（此前照搬夸克转存链路，
     * save 恒返回 403 / code 41020）。**`entry=ft` 是 UC 特有参数，不能丢**（§2）。
     *
     * @param body 请求体。
     * @return 统一响应包装体。
     */
    @POST("1/clouddrive/file/download?entry=ft&fr=pc&pr=UCBrowser")
    suspend fun getDownloadUrl(
        @Body body: UcDownloadRequest
    ): UcResponse<List<UcDownloadUrl>>

    /**
     * 列出**本账号**个人网盘指定目录的子项。
     *
     * 请求：`GET https://pc-api.uc.cn/1/clouddrive/file/sort?pr=UCBrowser&fr=pc&pdir_fid=...`
     * 响应：`data.list[]`。
     *
     * 依据：《抓包事实.md》§10.2「个人网盘文件 / 目录列表」——UC 走 `file/sort`
     * （与夸克同款，符合「UC 与夸克同构」的结论）。
     *
     * ⚠️ 文档冲突（R3 登记，待用户核）：§2「个人文件列表」写作 `1/clouddrive/file`，
     * 与 §10.2 的 `file/sort` 不一致。本实现采用 §10.2（专章、且与夸克同构）；
     * 若实测 `file/sort` 不通，请以抓包为准，改回 `file` 并回填本节。
     *
     * @param params 查询参数键值对（**必须**包含 `pr`/`fr`）。
     * @return 统一响应包装体。
     */
    @GET("1/clouddrive/file/sort")
    suspend fun listFiles(
        @QueryMap params: Map<String, String>
    ): UcResponse<UcFileList>

    /**
     * 查询**个人网盘**容量（空间用量）。
     *
     * 请求：`GET https://pc-api.uc.cn/1/clouddrive/member?pr=UCBrowser&fr=pc&fetch_subscribe=true&_ch=home`
     * 响应：`data.use_capacity`（已用）/ `data.total_capacity`（总量）。
     *
     * 依据：《抓包事实.md》§10.1——UC 与夸克同构，容量走 `member`。
     *
     * @param params 查询参数键值对。
     * @return 统一响应包装体。
     */
    @GET("1/clouddrive/member")
    suspend fun getMember(
        @QueryMap params: Map<String, String>
    ): UcResponse<UcMember>

    /**
     * 删除本账号文件 / 目录（移入回收站，非物理删除）。
     *
     * 请求：`POST https://pc-api.uc.cn/1/clouddrive/file/delete?pr=UCBrowser&fr=pc`
     * 请求体见 [UcDeleteRequest]。
     * 响应：`data.task_id`（异步任务）。
     *
     * 依据：《抓包事实.md》§10.3——UC 与夸克同构（夸克多带 `uc_param_str=`，UC 不带）。
     *
     * @param body 请求体。
     * @return 统一响应包装体，data 含 task_id。
     */
    @POST("1/clouddrive/file/delete?pr=UCBrowser&fr=pc")
    suspend fun deleteFiles(
        @Body body: UcDeleteRequest
    ): UcResponse<UcSaveResult>

    /**
     * 在**本账号**个人网盘创建目录（转存临时目录用）。
     *
     * 请求：`POST https://pc-api.uc.cn/1/clouddrive/file?pr=UCBrowser&fr=pc`
     * 请求体：`{"pdir_fid":"0","file_name":"...","dir_path":"","dir_init_lock":false}`。
     * 响应：`data.fid`。
     *
     * 依据：《抓包事实.md》§6.1⑥（夸克 / UC 请求体一致）。
     *
     * @param body 请求体。
     * @return 统一响应包装体。
     */
    @POST("1/clouddrive/file?pr=UCBrowser&fr=pc")
    suspend fun createFolder(
        @Body body: UcCreateFolderRequest
    ): UcResponse<UcCreateFolderResult>
}

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
 * 分享详情响应体（优先 `detail_info.list`，兼容 `list`）。
 *
 * @property detail_info 两级结构（真实）。
 * @property list 扁平结构（兼容）。
 */
@Serializable
data class UcShareDetail(
    val detail_info: UcDetailInfo? = null,
    val list: List<UcFile> = emptyList()
) {
    /** 条目列表：优先 `detail_info.list`，回退 `list`。 */
    val entries: List<UcFile>
        get() = detail_info?.list ?: list
}

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
 * `v2/detail` 请求体（《抓包事实.md》§6.1②）。
 *
 * @property pwd_id 分享 ID。
 * @property passcode 提取码（无则空串）。
 * @property pdir_fid 目标目录 fid（根为 `0`）。
 * @property force 固定 0。
 * @property page 页码。
 * @property size 每页数量。
 * @property fetch_banner 固定 1。
 * @property fetch_share 固定 1。
 * @property fetch_total 固定 1。
 * @property sort 排序表达式。
 * @property banner_platform 固定 `other`。
 * @property web_platform 固定 `windows`。
 * @property fetch_error_background 固定 1。
 */
@Serializable
data class UcShareDetailRequest(
    val pwd_id: String,
    val passcode: String = "",
    val pdir_fid: String,
    val force: Int = 0,
    val page: Int = 1,
    val size: Int = 50,
    val fetch_banner: Int = 1,
    val fetch_share: Int = 1,
    val fetch_total: Int = 1,
    val sort: String = "file_type:asc,file_name:asc",
    val banner_platform: String = "other",
    val web_platform: String = "windows",
    val fetch_error_background: Int = 1
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
 * 转存任务响应体。
 *
 * 完成判定（《抓包事实.md》§6.1④，三个任一满足即为完成）：
 * `finished_at > 0` || `status == 2` || `task_status == 2`。
 *
 * @property status 状态码；2 表示完成。
 * @property task_status 任务状态码；2 表示完成（另一字段名，兼容用）。
 * @property finished_at 完成时间戳；>0 表示完成。
 * @property save_as 转存结果。
 */
@Serializable
data class UcTask(
    val status: Int = 0,
    val task_status: Int = 0,
    val finished_at: Long = 0L,
    val save_as: UcSaveAs? = null
)

/**
 * 转存结果。
 *
 * @property save_as_top_fids 转存后本账号的新 fid 列表。
 */
@Serializable
data class UcSaveAs(
    val save_as_top_fids: List<String> = emptyList()
)

/**
 * 转存请求体（§6.1③）。
 *
 * @property pwd_id 分享 ID。
 * @property stoken 分享令牌。
 * @property pdir_fid 目标目录 fid。
 * @property to_pdir_fid 同 [pdir_fid]。
 * @property fid_list 待转存 fid 列表。
 * @property fid_token_list 与 [fid_list] 一一对应的 `share_fid_token`。
 * @property scene 固定 `link`。
 */
@Serializable
data class UcSaveRequest(
    val pwd_id: String,
    val stoken: String,
    val pdir_fid: String,
    val to_pdir_fid: String,
    val fid_list: List<String>,
    val fid_token_list: List<String>,
    val scene: String
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
 * 创建目录请求体（§6.1⑥，与夸克一致）。
 *
 * @property pdir_fid 父目录 fid（根为 `0`）。
 * @property file_name 新目录名。
 * @property dir_path 固定空串。
 * @property dir_init_lock 固定 false。
 */
@Serializable
data class UcCreateFolderRequest(
    val pdir_fid: String,
    val file_name: String,
    val dir_path: String = "",
    val dir_init_lock: Boolean = false
)

/**
 * 创建目录响应体。
 *
 * @property fid 新目录 fid。
 */
@Serializable
data class UcCreateFolderResult(
    val fid: String = ""
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