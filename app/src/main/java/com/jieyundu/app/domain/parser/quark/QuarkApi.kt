// 文件：QuarkApi.kt
// 职责：夸克网盘分享解析的 Retrofit 接口定义与响应体占位结构
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
 * 接口契约来源：《要求.md》7.6，方法名、注解、返回类型均与文档逐字一致。
 *
 * 未决事项（均由 di/NetworkModule 提供，不走本接口）：
 * - BaseUrl：`https://drive-pc.quark.cn/`
 * - 固定请求头：User-Agent（必须伪装夸克 PC 客户端）、Referer、Cookie（含 __puus）
 * - 超时：连接 15s / 读 30s / 写 30s（编码风格 C4）
 *
 * // TODO(用户抓包): 确认除 __puus 外是否还需要其他 cookie（如 __pus / __kp 等）
 * // TODO(用户抓包): 确认三个接口各自要求的固定 Header 全集
 */
interface QuarkApi {

    /**
     * 第 2 步：获取 stoken（临时令牌）。
     *
     * 请求：`POST https://drive-pc.quark.cn/1/clouddrive/share/sharepage/token`
     *
     * // TODO(用户抓包): 确认请求体除 pwd_id、passcode 外是否还有固定参数
     * // TODO(用户抓包): 确认 passcode 为空时该参数是缺省还是传空串
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
     *
     * // TODO(用户抓包): 确认除 pwd_id、stoken、pdir_fid 外是否还有固定查询参数
     * // TODO(用户抓包): 确认是否需要分页参数（_page / _size）以及单页上限
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
     * 请求：`POST https://drive-pc.quark.cn/1/clouddrive/file/download`
     *
     * // TODO(用户抓包): 确认请求体的真实字段名与嵌套结构（当前按 fid 列表占位）
     * // TODO(用户抓包): 确认直链有效期时长，用于决定过期后是否自动重解析
     *
     * @param body 请求体，值为任意类型（存在数组字段）。
     * @return 统一响应包装体，data 为直链列表。
     */
    @POST("1/clouddrive/file/download")
    suspend fun getDownloadUrl(
        @Body body: Map<String, Any>
    ): QuarkResponse<List<QuarkDownloadUrl>>
}

/**
 * 夸克接口的统一响应包装体。
 *
 * // TODO(用户抓包): 核对成功码取值与风控错误码含义
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
 * // TODO(用户抓包): 核对 stoken 字段名与其有效期字段
 *
 * @property stoken 临时令牌。
 */
@Serializable
data class QuarkShareToken(
    val stoken: String
)

/**
 * 分享详情响应体。
 *
 * // TODO(用户抓包): 核对列表字段名、是否含分页字段与分享标题字段
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
 * // TODO(用户抓包): 核对字段名（当前保持《要求.md》7.6 给出的蛇形命名）
 * // TODO(用户抓包): 补充判定文件夹的字段名与文件夹体积字段
 *
 * @property fid 文件 ID。
 * @property file_name 文件名。
 * @property size 文件大小，单位字节。
 */
@Serializable
data class QuarkFile(
    val fid: String,
    val file_name: String,
    val size: Long
)

/**
 * 下载直链条目。
 *
 * // TODO(用户抓包): 核对直链字段名
 *
 * @property download_url 下载直链。
 */
@Serializable
data class QuarkDownloadUrl(
    val download_url: String
)
