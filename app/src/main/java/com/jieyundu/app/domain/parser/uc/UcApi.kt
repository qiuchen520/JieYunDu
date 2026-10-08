// 文件：UcApi.kt
// 职责：UC 网盘 Retrofit 接口定义（分享 token / transfer_share/detail / 取链 / 个人网盘读写）
// 依赖：Retrofit、kotlinx.serialization（数据结构见同包 uc/UcModels.kt）
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.uc

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.QueryMap

/**
 * UC 网盘解析接口（`pc-api.uc.cn`）。
 *
 * 接口契约来源：《抓包事实.md》§2「UC —— 与夸克同构，域名/参数/UA 全不同」与 §6.1
 * 「夸克 / UC 共用结构」。UC 与夸克同源，但**分享取链走「免转存型」链路**（token →
 * transfer_share/detail → file/download），与夸克的「转存型」（token → detail → save →
 * task → file/download）不同；**域名、平台参数（`pr=UCBrowser`）、列表走
 * `transfer_share/detail`、取链带 `entry=ft`** 亦与夸克不同，故单独定义、不共用夸克接口。
 *
 * 依据：《参考实现_源码通读研究.md》§9.3——UC 分享文件可直接取链，无需 save/task/
 * 临时目录；`saveShare`/`getTask`/`createFolder` 仅作降级备用保留。
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
