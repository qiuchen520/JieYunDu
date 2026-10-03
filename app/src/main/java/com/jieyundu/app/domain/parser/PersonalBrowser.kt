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
}
