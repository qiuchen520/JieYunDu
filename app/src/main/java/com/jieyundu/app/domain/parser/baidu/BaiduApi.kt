// 文件：BaiduApi.kt
// 职责：百度网盘接口定义（分享提取码校验 / 分享文件列表）与响应模型
// 依赖：Retrofit、kotlinx.serialization、JsonPrimitive
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.baidu

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 百度网盘接口（当前仅覆盖「分享解析」链路：提取码校验 + 分享文件列表）。
 *
 * 说明（《抓包事实.md》§3 / §6.2 / §11.3）：
 * - 百度全部为 `Cookie` 认证，成功判定统一为 **`errno == 0`**（不是夸克/UC 的 `code`）；
 * - 本接口只声明**已有抓包证据**的端点，参数不猜测（铁律 R3）；
 * - User-Agent 由 `NetworkModule` 按「host + 路径」统一选择（百度同一个 host 上混用两套 UA），
 *   因此这里**不写** UA 头；只有动态的 `Referer`（含分享短码）通过参数传入。
 */
interface BaiduApi {

    /**
     * 校验提取码并换取 `randsk`。
     *
     * 请求：`POST https://pan.baidu.com/share/verify?surl=<短码>`
     * 请求体（form）：`pwd=<提取码>&vcode_str=&vcode=`
     * 响应：`randsk`（**URL 编码形态，可直接当 `sekey` 使用**）；同时 `Set-Cookie: BDCLND=<randsk>`
     *   —— 该 Cookie 由网络层的响应 Cookie 拦截器登记，**子目录列表必须带它**（否则 `errno=2`）。
     *
     * 依据：《抓包事实.md》§3「验证提取码」、§6.2、§11.3 #2。
     *
     * @param surl 分享短码（`/s/` 之后、**去掉前导 `1`**）。
     * @param pwd 提取码（4 位）。
     * @param vcodeStr 验证码字符串；抓包为空串。
     * @param vcode 验证码；抓包为空串。
     * @param referer 动态 Referer，形如 `https://pan.baidu.com/s/<短码>`。
     * @return 统一响应体。
     */
    @FormUrlEncoded
    @POST("share/verify")
    suspend fun verifyShare(
        @Query("surl") surl: String,
        @Field("pwd") pwd: String,
        @Field("vcode_str") vcodeStr: String = "",
        @Field("vcode") vcode: String = "",
        @Header("Referer") referer: String
    ): BaiduShareVerifyResponse

    /**
     * 取分享文件列表（顶层或某子目录）。
     *
     * 请求：`GET https://pan.baidu.com/rest/2.0/xpan/share?method=list&shorturl=<短码>&page=1&num=100`
     *   `&root=<1 顶层 / 0 子目录>&dir=<urlencode 路径>[&sekey=<randsk>]`
     *
     * 依据：《抓包事实.md》§11.3 #3、§6.2——注意 `root` 的取值**容易写反**：
     * 顶层是 `1`、子目录是 `0`。
     *
     * @param method 固定 `list`。
     * @param shorturl 分享短码。
     * @param page 页码，从 1 开始。
     * @param num 每页条数（抓包为 100）。
     * @param root `1` 表示顶层、`0` 表示子目录。
     * @param dir 子目录的**完整路径**（根为 `/`）；顶层不传。
     * @param sekey 提取码校验得到的 `randsk`（**已是 URL 编码形态**，故 `encoded = true`）。
     * @return 统一响应体。
     */
    @GET("rest/2.0/xpan/share")
    suspend fun listShareFiles(
        @Query("method") method: String = METHOD_LIST,
        @Query("shorturl") shorturl: String,
        @Query("page") page: Int = FIRST_PAGE,
        @Query("num") num: Int = PAGE_SIZE,
        @Query("root") root: Int,
        @Query("dir") dir: String? = null,
        @Query("sekey", encoded = true) sekey: String? = null
    ): BaiduShareListResponse

    companion object {
        /** 分享列表固定 `method` 值。 */
        const val METHOD_LIST = "list"

        /** 顶层目录的 `root` 取值（**易与子目录写反**）。 */
        const val ROOT_TOP_LEVEL = 1

        /** 子目录的 `root` 取值。 */
        const val ROOT_SUB_DIRECTORY = 0

        /** 首页页码。 */
        const val FIRST_PAGE = 1

        /** 每页条数（《抓包事实.md》§11.3 #3 为 100）。 */
        const val PAGE_SIZE = 100

        /** 成功判定值（`errno == 0`）。 */
        const val SUCCESS_ERRNO = 0
    }
}

/**
 * `share/verify` 响应。
 *
 * @property errno 业务错误码；0 表示成功。
 * @property randsk 提取码换取的随机密钥（URL 编码形态，直接当 `sekey`）。
 * @property request_id 服务端请求 ID（诊断用）。
 */
@Serializable
data class BaiduShareVerifyResponse(
    val errno: Int = -1,
    val randsk: String? = null,
    val request_id: Long? = null
)

/**
 * `xpan/share?method=list` 响应。
 *
 * @property errno 业务错误码；0 表示成功。
 * @property title 分享标题。
 * @property share_id 分享 ID（数字；转存时需要）。
 * @property uk 分享者用户 ID（转存时作为 `from` 参数）。
 * @property list 条目列表。
 */
@Serializable
data class BaiduShareListResponse(
    val errno: Int = -1,
    val title: String? = null,
    val share_id: Long? = null,
    val uk: Long? = null,
    val list: List<BaiduShareFile> = emptyList()
)

/**
 * 分享内单个条目。
 *
 * 说明（《抓包事实.md》§11.3 #3）：**目录项要用 `path` 当 id，文件项用 `fs_id`**。
 *
 * @property fs_id 文件 ID（数字）。
 * @property server_filename 文件名。
 * @property size 大小（字节）；目录为 0。
 * @property isdir `"1"` 表示目录、`"0"` 表示文件。抓包为字符串，但为兼容数字形态，
 *   此处用 [JsonElement] 承载（其内置序列化器最稳），读取时统一按 `content` 比较，
 *   避免因服务端类型漂移抛序列化异常。
 * @property path 该条目的完整路径（目录的 id 来源）。
 * @property server_mtime 服务端修改时间（秒）。
 */
@Serializable
data class BaiduShareFile(
    val fs_id: Long? = null,
    val server_filename: String = "",
    val size: Long = 0,
    val isdir: JsonElement? = null,
    val path: String = "",
    val server_mtime: Long = 0
) {

    /** 是否为目录（`isdir` 为 `"1"`，兼容数字 `1`）。 */
    val isDirectory: Boolean
        get() = (isdir as? JsonPrimitive)?.content == IS_DIRECTORY_VALUE

    private companion object {
        /** `isdir` 表示目录的取值。 */
        const val IS_DIRECTORY_VALUE = "1"
    }
}
