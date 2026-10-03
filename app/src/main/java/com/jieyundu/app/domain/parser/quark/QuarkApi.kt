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
 * 接口契约来源：《要求.md》7.6，方法名、注解、返回类型均与文档逐字一致；
 * 参数名与响应字段已由 2026-10-03 的终端实测（Owner 授权 curl 探测，见 CHANGELOG）校准。
 *
 * 实测结论（2026-10-03）：
 * - BaseUrl：`https://drive-pc.quark.cn/`（由 di/NetworkModule 提供）；
 * - User-Agent：伪装夸克 PC 客户端（由 di/NetworkModule 统一注入）；
 * - **token 与 detail 接口无需任何 Cookie**（实测无 Cookie 直接 `code:0`）；
 * - `file/download` 接口会下发 `__pugs` Cookie（Domain=quark.cn，Max-Age=10800），
 *   CDN 直链（`dl-guest-*.drive.quark.cn`）**必须**携带该 Cookie，否则返回 HTTP 412；
 *   该 Cookie 由 di/NetworkModule 的响应拦截器登记到 CookieStore，供下载引擎注入。
 */
interface QuarkApi {

    /**
     * 第 2 步：获取 stoken（临时令牌）。
     *
     * 请求：`POST https://drive-pc.quark.cn/1/clouddrive/share/sharepage/token?pr=ucpro&fr=pc`
     * 请求体：`{"pwd_id":"<shareId>"}`；携带提取码时追加 `"passcode":"<pwd>"`。
     * 响应：`data.stoken`、`data.title`（分享标题）。
     *
     * 实测：无任何 Cookie 亦返回 `code:0`；无 `pwd_id` 时 `code:0` 但不含 stoken。
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
     * 响应：`data.list[]`，条目含 `fid/file_name/size/dir/share_fid_token`。
     *
     * 实测：无任何 Cookie 亦返回 `code:0`。
     *
     * @param params 查询参数键值对。
     * @return 统一响应包装体。
     */
    @GET("1/clouddrive/share/sharepage/detail")
    suspend fun getShareDetail(
        @QueryMap params: Map<String, String>
    ): QuarkResponse<QuarkShareDetail>

    /**
     * 第 4 步：获取下载直链（有时效，拿到后须立即下载）。
     *
     * 请求：`POST https://drive-pc.quark.cn/1/clouddrive/file/download?pr=ucpro&fr=pc`
     * 请求体：`{"fids":["<fid>", ...],"pwd_id":"<shareId>","stoken":"<stoken>"}`；
     * **必须携带 pwd_id 与 stoken**，否则返回 `code:31001 require login [share missing]`。
     * 响应：`data[]` 为文件对象列表，每个含 `fid` 与 `download_url`。
     * 副作用：响应头 `Set-Cookie: __pugs=...`（Domain=quark.cn），CDN 直链必需。
     *
     * @param body 请求体，值为任意类型（`fids` 为数组字段）。
     * @return 统一响应包装体，data 为带直链的文件条目列表。
     */
    @POST("1/clouddrive/file/download")
    suspend fun getDownloadUrl(
        @Body body: Map<String, Any>
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
 * 实测（2026-10-03）：
 * ```json
 * {"status":200,"code":0,"message":"ok","data":{"stoken":"...","title":"极简(直装版)", ...}}
 * ```
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
 * @property list 文件条目列表。
 */
@Serializable
data class QuarkShareDetail(
    val list: List<QuarkFile>
)

/**
 * 分享内的单个文件条目。
 *
 * 实测字段（节选）：`fid/file_name/size/dir/share_fid_token`。
 *
 * @property fid 文件 ID。
 * @property file_name 文件名。
 * @property size 文件大小，单位字节；文件夹为 0。
 * @property dir 是否为文件夹（实测字段 `dir`，true 表示文件夹）。
 */
@Serializable
data class QuarkFile(
    val fid: String,
    val file_name: String,
    val size: Long,
    val dir: Boolean = false
)

/**
 * 下载直链条目。
 *
 * 实测（2026-10-03）：`file/download` 的 `data[]` 是完整文件对象，
 * 每个条目同时含 `fid` 与 `download_url`，可按 `fid` 回填到对应文件。
 *
 * @property fid 文件 ID，用于与 detail 列表对应。
 * @property download_url 下载直链（指向 `dl-guest-*.drive.quark.cn`，须带 `__pugs` Cookie）。
 */
@Serializable
data class QuarkDownloadUrl(
    val fid: String = "",
    val download_url: String = ""
)