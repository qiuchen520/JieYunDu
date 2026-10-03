// 文件：ShareBrowser.kt
// 职责：定义「逐级展开分享目录」的统一能力接口（流程 A 文件夹展开 / 流程 B 个人网盘浏览共用）
// 依赖：FileInfo、NetdiskType
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser

import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType

/**
 * 分享目录浏览器统一接口。
 *
 * 存在理由（Owner 裁定：统一目录浏览器、流程 A/B 共用）：分享文件夹展开与个人网盘浏览
 * 是同一交互模型——「列出某目录的直接子项 → 点目录进入 → 带该目录 fid 再列 → 返回栈 pop」。
 * 本接口只抽象「取某一层的条目」，具体取法由各网盘实现（分享走 `sharepage/detail`，
 * 个人网盘走 `file/sort`），UI 侧只换底层实现。
 *
 * 实现方须保证：返回的条目按「文件夹在前、文件在后」排序（若服务端未保证），
 * 并保留每个条目的 `shareFidToken`（分享场景转存需要）。
 */
interface ShareBrowser {

    /** 本实现负责的网盘类型。 */
    val type: NetdiskType

    /**
     * 列出分享内指定目录的直接子项。
     *
     * @param pwdId 分享 ID。
     * @param stoken 分享临时令牌。
     * @param pdirFid 目标目录 fid；分享根目录通常为 `0`。
     * @return 该目录下的条目列表；请求失败或空目录返回空列表。
     */
    suspend fun listChildren(pwdId: String, stoken: String, pdirFid: String): List<FileInfo>
}
