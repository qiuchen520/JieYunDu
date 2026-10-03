// 文件：NetdiskServiceRouter.kt
// 职责：按网盘类型路由到对应的浏览 / 转存能力实现（多网盘支持，替代单例硬绑）
// 依赖：ShareBrowser、PersonalBrowser、ShareDownloadPreparer、NetdiskType
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser

import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.transfer.ShareDownloadPreparer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网盘能力路由器（B2：多网盘支持）。
 *
 * 背景：阶段 9 之前，[ShareBrowser] / [PersonalBrowser] / [ShareDownloadPreparer] 在 DI 里
 * **硬绑夸克单例**，导致 UC 无法接入。B2 起改为「Hilt 多绑定集合 + 本路由器按 type 取用」，
 * 新增网盘只需在对应集合里多绑定一个实现，调用方（ViewModel）不再感知具体网盘。
 *
 * 无对应实现时各方法返回 null，由调用方给出中性提示（不崩溃）。
 *
 * @param shareBrowsers 全部分享目录浏览器实现（多绑定注入）。
 * @param personalBrowsers 全部个人网盘浏览器实现（多绑定注入）。
 * @param preparers 全部分享转存器实现（多绑定注入）。
 */
@Singleton
class NetdiskServiceRouter @Inject constructor(
    private val shareBrowsers: Set<@JvmSuppressWildcards ShareBrowser>,
    private val personalBrowsers: Set<@JvmSuppressWildcards PersonalBrowser>,
    private val preparers: Set<@JvmSuppressWildcards ShareDownloadPreparer>
) {

    /**
     * 取指定网盘的分享目录浏览器。
     *
     * @param type 网盘类型。
     * @return 对应实现；无则 null。
     */
    fun shareBrowserFor(type: NetdiskType): ShareBrowser? =
        shareBrowsers.firstOrNull { browser -> browser.type == type }

    /**
     * 取指定网盘的个人网盘浏览器。
     *
     * @param type 网盘类型。
     * @return 对应实现；无则 null。
     */
    fun personalBrowserFor(type: NetdiskType): PersonalBrowser? =
        personalBrowsers.firstOrNull { browser -> browser.type == type }

    /**
     * 取指定网盘的分享转存器。
     *
     * @param type 网盘类型。
     * @return 对应实现；无则 null。
     */
    fun shareDownloadPreparerFor(type: NetdiskType): ShareDownloadPreparer? =
        preparers.firstOrNull { preparer -> preparer.type == type }
}