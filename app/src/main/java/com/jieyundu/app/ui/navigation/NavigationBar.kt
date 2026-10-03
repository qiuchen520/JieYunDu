// 文件：NavigationBar.kt
// 职责：手机端导航 wrapper——调用 TopGlassNavBar 并传入横向方向
// 依赖：TopGlassNavBar、Compose Foundation layout、Dimens
// 协议：AGPL-3.0

package com.jieyundu.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jieyundu.app.ui.theme.Dimens

/**
 * 手机端导航（顶部横向玻璃条，宽度 = 屏宽 − 32dp，高度 64dp）。
 *
 * @param labels 页签文案（来自 strings.xml）。
 * @param selectedIndex 当前选中下标。
 * @param onSelect 选中回调。
 * @param modifier 外部修饰符（调用方通常传入 `fillMaxWidth`）。
 */
@Composable
fun NavigationBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    TopGlassNavBar(
        labels = labels,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        orientation = GlassBarOrientation.Horizontal,
        modifier = modifier.padding(horizontal = Dimens.NavBarLengthInsetHorizontal)
    )
}