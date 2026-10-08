// 文件：QuarkApi.kt
// 职责：夸克网盘 Retrofit 接口定义（分享 token / 详情 / 转存 / 轮询 / 取链 / 个人网盘读写）
// 依赖：Retrofit、kotlinx.serialization（数据结构见同包 quark/QuarkModels.kt）
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.quark

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.QueryMap


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
     * 请求：`POST https://drive-pc.quark.cn/1/clouddrive/share/sharepage/save?pr=ucpro&fr=pc`
     * 请求体：`{"pwd_id","stoken","fid_list":[...],"fid_token_list":[...],"scene":"link"}`；
     * `fid_token_list` 为每个文件的 `share_fid_token`。
     * 响应：`data.task_id`（异步任务 ID，需轮询，见 [getTask]）。
     *
     * 说明（B1.2）：`pr` / `fr` 为《抓包事实.md》§1 记录的固定查询参数，此前注解漏写，
     * 现补齐以对齐抓包（未获证据前不改动请求体字段）。
     *
     * @param body 请求体键值对（含数组字段）。
     * @return 统一响应包装体，data 含 task_id。
     */
    @POST("1/clouddrive/share/sharepage/save?pr=ucpro&fr=pc")
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

    /**
     * 列出**本账号**个人网盘指定目录的子项（用于查临时目录、空目录检测）。
     *
     * 请求：`GET https://drive-pc.quark.cn/1/clouddrive/file/sort`
     * 查询参数：`pr`、`fr`、`pdir_fid`（根为 `0`）、`_page`、`_size`、`_fetch_total`、
     * `_fetch_sub_dirs`、`_sort`。
     * 响应：`data.list[]`（字段与分享条目一致，但无 `share_fid_token`）。
     *
     * 依据：《抓包事实.md》§10.2「个人网盘文件 / 目录列表」。
     *
     * 写法约定（B1.3，依《评审清单.md》§11.1 建议）：`pr` / `fr` **不写死在路径里**，
     * 与 `detail` / `member` / `save` 保持一致，全部由调用方的 `QueryMap` 提供。
     * 这样「固定参数」只有一个来源，不会再出现路径与参数各带一次的重复
     * （该重复曾在装机日志中实测出现）。
     *
     * @param params 查询参数键值对（**必须**包含 `pr` / `fr`）。
     * @return 统一响应包装体。
     */
    @GET("1/clouddrive/file/sort")
    suspend fun listFiles(
        @QueryMap params: Map<String, String>
    ): QuarkResponse<QuarkFileList>

    /**
     * 查询**本账号**个人网盘容量信息（已用 / 总量）。
     *
     * 请求：`GET https://drive-pc.quark.cn/1/clouddrive/member?pr=ucpro&fr=pc&fetch_subscribe=true&_ch=home`
     * 响应：`data.use_capacity`（已用）/ `data.total_capacity`（总量）。
     *
     * 依据：《抓包事实.md》§10.1「容量 / 空间查询」。
     *
     * @param params 查询参数键值对。
     * @return 统一响应包装体，data 含容量。
     */
    @GET("1/clouddrive/member")
    suspend fun getMember(
        @QueryMap params: Map<String, String>
    ): QuarkResponse<QuarkMember>

    /**
     * 在**本账号**个人网盘创建目录（用于创建 `.极云渡临时`）。
     *
     * 请求：`POST https://drive-pc.quark.cn/1/clouddrive/file?pr=ucpro&fr=pc`
     * 请求体：`{"pdir_fid":"<父目录，根为 0>","file_name":"<新目录名>",
     * "dir_path":"","dir_init_lock":false}`。
     * 响应：`data.fid`（新目录 fid）。
     *
     * 依据：《抓包事实.md》§6.1⑥ 与 §9.1（夸克 / UC 请求体一致）。
     *
     * @param body 请求体。
     * @return 统一响应包装体，data 含新目录 fid。
     */
    @POST("1/clouddrive/file?pr=ucpro&fr=pc")
    suspend fun createFolder(
        @Body body: QuarkCreateFolderRequest
    ): QuarkResponse<QuarkCreateFolderResult>

    /**
     * 删除**本账号**个人网盘中的文件 / 目录（移入回收站语义）。
     *
     * 请求：`POST https://drive-pc.quark.cn/1/clouddrive/file/delete?pr=ucpro&fr=pc&uc_param_str=`
     * 请求体：`{"action_type":2,"filelist":["<fid>"],"exclude_fids":[]}`。
     * 响应：`data.task_id`（异步任务 ID）。
     *
     * 依据：《抓包事实.md》§10.3。
     *
     * @param body 请求体。
     * @return 统一响应包装体。
     */
    @POST("1/clouddrive/file/delete?pr=ucpro&fr=pc&uc_param_str=")
    suspend fun deleteFiles(
        @Body body: QuarkDeleteRequest
    ): QuarkResponse<QuarkDeleteResult>
}
