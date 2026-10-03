// 文件：WindowSizeHelper.kt
// 职责：窗口尺寸判断——决定使用平板（竖直导航）还是手机（横向导航）
// 依赖：Compose UI platform（LocalConfiguration）
// 协议：AGPL-3.0

package com.jieyundu.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** 平板判定最小宽度（dp）。 */
private const val TABLET_MIN_WIDTH_DP = 600

/**
 * 判断当前是否为平板（expanded）布局。
 *
 * 说明：以 [LocalConfiguration] 的可用宽度做断点判断。
 * 《要求.md》9.4 提及的 `currentWindowAdaptiveInfo().windowSizeClass` 属于
 * `material3-adaptive`，未列入第五部分技术栈（D8），故不引入；此处以等价的
 * 宽度断点实现同一语义。
 *
 * @return true 表示平板（左侧竖直导航）；false 表示手机（顶部横向导航）。
 */
@Composable
fun rememberIsExpandedLayout(): Boolean {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return widthDp >= TABLET_MIN_WIDTH_DP
}