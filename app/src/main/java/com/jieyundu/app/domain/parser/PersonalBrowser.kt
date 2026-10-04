// 文件：PersonalBrowser.kt
// 职责：定义「浏览本账号个人网盘」的统一能力接口（流程 B 网盘管理）
// 依赖：FileInfo、QuotaInfo、NetdiskType
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser

import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.QuotaInfo

/**
 * 个人网盘浏览器统一接口（流程 B：网盘管理）。
 *
 * 存在理由：与 [ShareBrowser]（流程 A：分享展开）对称。个人网盘浏览不需要 pwdId/stoken，
 * 只需「某目录 fid」；UI 交互模型完全一致（列目录 → 点目录进入 → 返回上一级）。
 * 具体取法由各网盘实现——夸克 `file/sort`（§10.2），UC 同构，百度 `api/list`，迅雷 `drive/v1/files`。
 *
 * 实现约束：返回条目应保证「文件夹在前、文件在后」；个人网盘条目的 `shareFidToken` 为空串。
 */
interface PersonalBrowser {

    /** 本实现负责的网盘类型。 */
    val type: NetdiskType

    /**
     * 列出个人网盘指定目录的直接子项。
     *
     * @param pdirFid 目标目录 fid；根目录通常为 `0`（百度为 `/`）。
     * @return 该目录下的条目列表；请求失败或空目录返回空列表。
     */
    suspend fun listPersonalChildren(pdirFid: String): List<FileInfo>

    /**
     * 查询个人网盘容量（已用 / 总量）。
     *
     * @return 容量信息；查询失败返回 null。
     */
    suspend fun fetchQuota(): QuotaInfo?

    /**
     * 删除**个人网盘**中的文件 / 目录（网盘管理页「删除」入口，§10.3 `file/delete`）。
     *
     * 说明（【JYD-BROWSER-2026-10-04】）：
     * - 语义为「移入回收站」（`action_type = 2`），可从网盘回收站恢复，不是物理抹除；
     * - 调用方（UI）**必须先**让用户显式确认，再经
     *   `TempFolderGuard.mayDeleteUserInitiated(fid, userInitiated = true)` 放行——
     *   本方法只负责协议调用，安全判定不在这一层；
     * - 实现方不得在此方法内自行扩大删除范围（例如顺带清理临时目录）。
     *
     * @param fid 目标文件 / 目录 fid。
     * @return true 表示服务端接受删除请求。
     */
    suspend fun deletePersonalFile(fid: String): Boolean

    /**
     * 取**个人网盘**文件的直链（网盘管理页「下载到本地」入口）。
     *
     * 说明（R3：不编造参数）：
     * - 夸克 `file/download` 只认 `fids`（本账号 fid），个人文件可直接取链；
     * - UC 的 `file/download` 请求体**强制要求** `pwd_id` / `stoken` / `fids_token`
     *   （分享态字段），个人文件的取链报文**尚无抓包依据**，故 UC 实现返回 null，
     *   由 UI 提示「暂不支持直接下载自己网盘的文件」——绝不猜测参数。
     *
     * @param fid 目标文件 fid。
     * @return 可下载直链；不支持或失败时返回 null。
     */
    suspend fun fetchPersonalDownloadUrl(fid: String): String?
}
