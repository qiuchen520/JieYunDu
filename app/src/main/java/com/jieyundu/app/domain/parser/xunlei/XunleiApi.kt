// 文件：XunleiApi.kt
// 职责：迅雷业务主机 Retrofit 接口定义（分享解析 / 分享子目录 / 配额）
// 依赖：Retrofit、XunleiModels
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.xunlei

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 迅雷业务接口（`api-pan.xunlei.com`，【JYD-XUNLEI-P1A-2026-10-08】）。
 *
 * 认证与设备头（`Authorization: Bearer` / `X-Client-Id` / `X-Device-Id` /
 * `X-Client-Version` / `X-Captcha-Token` / `Origin` / `Referer`）由
 * `data/remote/XunleiAuthInterceptor` 统一注入——本接口签名只描述**端点与查询参数**，
 * 与夸克 / UC 的既有做法一致（不改接口签名、不在路径里写死鉴权）。
 *
 * 游客可用性（§11.4 #10）：分享解析与子目录接口「Bearer 或游客匿名（**不写 Authorization**）」，
 * 因此未登录也能解析分享——未登录时拦截器不带 Authorization 头。
 */
interface XunleiApi {

    /**
     * 分享解析（§11.4 #10）。
     *
     * 请求：`GET https://api-pan.xunlei.com/drive/v1/share?share_id=&pass_code=&limit=100&page_token=&thumbnail_size=SIZE_SMALL`
     * 响应：`title / files[] / pass_code_token / next_page_token`，另有 `share_status`：
     * `PASS_CODE_EMPTY`（无提取码）/ `PASS_CODE_ERROR`（提取码错误）/ `PASS_CODE_NEED`（需要提取码）。
     *
     * @param shareId 分享 ID（`/s/` 之后一段）。
     * @param passCode 提取码；无码分享传 null（Retrofit 会省略该查询参数）。
     * @param limit 每页条数（§11.4 为 100）。
     * @param pageToken 翻页游标。
     * @param thumbnailSize 缩略图档位（§11.4 为 `SIZE_SMALL`）。
     * @return 分享解析响应。
     */
    @GET("drive/v1/share")
    suspend fun share(
        @Query("share_id") shareId: String,
        @Query("pass_code") passCode: String?,
        @Query("limit") limit: Int = XunleiConfig.SHARE_PAGE_SIZE,
        @Query("page_token") pageToken: String? = null,
        @Query("thumbnail_size") thumbnailSize: String = XunleiConfig.THUMBNAIL_SIZE_SMALL
    ): XunleiShareResponse

    /**
     * 分享子目录列表（§11.4 #11）。
     *
     * 请求：`GET .../drive/v1/share/detail?share_id=&parent_id=&pass_code_token=&limit=100&page_token=&thumbnail_size=SIZE_SMALL`
     *
     * @param shareId 分享 ID。
     * @param parentId 父目录 id（分享内某目录的 id）。
     * @param passCodeToken 提取码令牌（来自 [share] 的 `pass_code_token`）。
     * @param limit 每页条数。
     * @param pageToken 翻页游标。
     * @param thumbnailSize 缩略图档位。
     * @return 子目录响应。
     */
    @GET("drive/v1/share/detail")
    suspend fun shareDetail(
        @Query("share_id") shareId: String,
        @Query("parent_id") parentId: String,
        @Query("pass_code_token") passCodeToken: String?,
        @Query("limit") limit: Int = XunleiConfig.SHARE_PAGE_SIZE,
        @Query("page_token") pageToken: String? = null,
        @Query("thumbnail_size") thumbnailSize: String = XunleiConfig.THUMBNAIL_SIZE_SMALL
    ): XunleiShareDetailResponse

    /**
     * 配额 / 账号信息（§11.4 #14：`data.quota.limit / usage / usage_in_trash`）。
     *
     * 说明：阶段 3（个人网盘管理）才使用；本批一并声明以固定端点口径。
     * 另：登录态校验也以该端点为准（Bearer 有效即 200）。
     *
     * @return 配额响应。
     */
    @GET("drive/v1/about")
    suspend fun about(): XunleiAboutResponse
}
