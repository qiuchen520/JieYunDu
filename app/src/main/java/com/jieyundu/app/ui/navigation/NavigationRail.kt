// 文件：NavigationRail.kt
// 职责：平板端导航 wrapper——调用 TopGlassNavBar 并传入竖直方向
// 依赖：TopGlassNavBar、Compose Foundation layout、Dimens
// 协议：AGPL-3.0

package com.jieyundu.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jieyundu.app.ui.theme.Dimens

/**
 * 平板端导航（左侧竖直玻璃条，宽度 96dp，高度撑满可用空间）。
 *
 * @param labels 页签文案（来自 strings.xml）。
 * @param selectedIndex 当前选中下标。
 * @param onSelect 选中回调。
 * @param modifier 外部修饰符（调用方通常传入 `fillMaxHeight`）。
 * @param preset 弹簧预设（阶段 7 固定为 [JellyPreset]，透传至 [TopGlassNavBar]）。
 */
@Composable
fun NavigationRail(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    preset: NavSpringPreset = JellyPreset
) {
    TopGlassNavBar(
        labels = labels,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        orientation = GlassBarOrientation.Vertical,
        preset = preset,
        modifier = modifier.padding(vertical = Dimens.SpaceLg)
    )
}
