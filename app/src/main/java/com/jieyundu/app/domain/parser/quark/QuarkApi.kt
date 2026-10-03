// 文件：QuarkApi.kt
// 职责：夸克网盘分享解析的 Retrofit 接口定义与响应体结构（参数/字段已按真实接口实测校准）
// 依赖：Retrofit、kotlinx.serialization
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.quark

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.QueryMap

/**
 * 夸克网盘（pan.quark.cn / drive-pc.quark.cn）解析接口。
 *
 * 接口契约来源：《要求.md》7.6 与【修订 JYD-PARSE-2026-10-03】（依《解析Bug分析.md》）；
 * 参数名与响应字段已由 2026-10-03 的终端实测（Owner 授权 curl 探测，见 CHANGELOG）校准。
 *
 * 完整链路（7 步）：握手 → token → detail → **save（转存）** → **task（轮询）** →
 * file/download（传**自己网盘**的 fid）→ 下载（须带 `__pugs`）。
 * 其中 save + task 不可省略：`file/download` 只认自己网盘里的文件。
 */
interface QuarkApi {

    /**
     * 第 2 步：获取 stoken（临时令牌）。
     *
     * 请求：`POST https://drive-pc.quark.cn/1/clouddrive/share/sharepage/token?pr=ucpro&fr=pc`
     * 请求体：`{"pwd_id":"<shareId>","support_visit_limit_private_share":true}`；
     * 携带提取码时追加 `"passcode":"<pwd>"`。
     * 响应：`data.stoken`、`data.title`（分享标题）。
     *
     * @param body 请求体键值对。
     * @return 统一响应包装体。
     */
    @POST("1/clouddrive/share/sharepage/token")
    suspend fun getShareToken(
        @Body body: Map<String, String>
    ): QuarkResponse<QuarkShareToken>

    /**
     * 第 3 步：获取分享文件列表。
     *
     * 请求：`GET https://drive-pc.quark.cn/1/clouddrive/share/sharepage/detail`
     * 查询参数：`pr/fr/pwd_id/stoken/pdir_fid/force/_page/_size/_sort`；
     * `pdir_fid=0` 表示分享根目录。
     * 响应：真实结构为 **`data.detail_info.list[]`**（兼容旧写法 `data.list[]`）；
     * 条目含 `fid/file_name/size/dir/share_fid_token`。
     *
     * @param params 查询参数键值对。
     * @return 统一响应包装体。
     */
    @GET("1/clouddrive/share/sharepage/detail")
    suspend fun getShareDetail(
        @QueryMap params: Map<String, String>
    ): QuarkResponse<QuarkShareDetail>

    /**
     * 第 4 步：把分享中的文件**转存到本账号**（save）。
     *
     * 请求：`POST https://drive-pc.quark.cn/1/clouddrive/share/sharepage/save`
     * 请求体：`{"pwd_id","stoken","fid_list":[...],"fid_token_list":[...],"scene":"link"}`；
     * `fid_token_list` 为每个文件的 `share_fid_token`。
     * 响应：`data.task_id`（异步任务 ID，需轮询，见 [getTask]）。
     *
     * @param body 请求体键值对（含数组字段）。
     * @return 统一响应包装体，data 含 task_id。
     */
    @POST("1/clouddrive/share/sharepage/save")
    suspend fun saveShare(
        @Body body: QuarkSaveRequest
    ): QuarkResponse<QuarkSaveResult>

    /**
     * 第 5 步：轮询转存任务状态（task）。
     *
     * 请求：`GET https://drive-pc.quark.cn/1/clouddrive/task?pr=ucpro&fr=pc&task_id=...`
     * 响应：`data.status`（2 表示完成）、`data.finished_at`（>0 表示完成）、
     * `data.save_as.save_as_top_fids[]`（本账号中的**新 fid**）。
     *
     * @param params 查询参数键值对。
     * @return 统一响应包装体。
     */
    @GET("1/clouddrive/task")
    suspend fun getTask(
        @QueryMap params: Map<String, String>
    ): QuarkResponse<QuarkTask>

    /**
     * 第 6 步：获取下载直链（有时效，拿到后须立即下载）。
     *
     * 请求：`POST https://drive-pc.quark.cn/1/clouddrive/file/download?pr=ucpro&fr=pc&sys=win32&ve=3.23.2`
     * 请求体：`{"fids":["<自己网盘的 fid>", ...]}`（字段名为 `fids`，**传转存后的新 fid**）。
     * 响应：`data[]` 为文件对象列表，每个含 `fid` 与 `download_url`。
     * 副作用：响应头 `Set-Cookie: __pugs=...`（Domain=quark.cn），CDN 直链必需。
     *
     * @param body 请求体，值为任意类型（`fids` 为数组字段）。
     * @return 统一响应包装体，data 为带直链的文件条目列表。
     */
    @POST("1/clouddrive/file/download?pr=ucpro&fr=pc&sys=win32&ve=3.23.2")
    suspend fun getDownloadUrl(
        @Body body: QuarkDownloadRequest
    ): QuarkResponse<List<QuarkDownloadUrl>>
}

/**
 * 夸克接口的统一响应包装体。
 *
 * 实测：成功 `code == 0`；风控/业务失败携带非 0 业务码
 * （例：`31001` 表示 share missing / 需登录）。
 *
 * @property code 业务状态码。
 * @property message 服务端描述。
 * @property data 业务数据。
 */
@Serializable
data class QuarkResponse<T>(
    val code: Int,
    val message: String,
    val data: T
)

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
 * @property fid_list 待转存的分享文件 fid 列表。
 * @property fid_token_list 与 [fid_list] 一一对应的 `share_fid_token` 列表。
 * @property scene 转存场景（固定 `link`，表示来自分享链接）。
 */
@Serializable
data class QuarkSaveRequest(
    val pwd_id: String,
    val stoken: String,
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