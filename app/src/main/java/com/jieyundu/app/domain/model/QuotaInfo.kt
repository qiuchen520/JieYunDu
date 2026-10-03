// 文件：QuotaInfo.kt
// 职责：描述网盘容量（已用 / 总量 / 回收站占用）
// 依赖：无
// 协议：AGPL-3.0

package com.jieyundu.app.domain.model

/**
 * 网盘容量信息（流程 B 网盘管理头部展示）。
 *
 * 依据：《抓包事实.md》§10.1——四家统一收敛到该模型；夸克 / UC / 百度无
 * 「回收站占用」字段，[usedInTrash] 取 0。
 *
 * @property used 已用容量（字节）。
 * @property total 总容量（字节）。
 * @property usedInTrash 回收站占用容量（字节）；无该字段时取 0。
 */
data class QuotaInfo(
    val used: Long,
    val total: Long,
    val usedInTrash: Long = 0L
) {
    /** 剩余容量（字节）；不会为负。 */
    val remaining: Long
        get() = (total - used).coerceAtLeast(0L)
}
