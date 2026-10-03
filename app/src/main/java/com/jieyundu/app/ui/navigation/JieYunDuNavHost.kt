// 文件：JieYunDuNavHost.kt
// 职责：导航宿主——按窗口尺寸选取导航形式，并承载页面内容
// 依赖：WindowSizeHelper、NavigationRail、NavigationBar、Compose runtime/foundation
// 协议：AGPL-3.0

package com.jieyundu.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jieyundu.app.R
import com.jieyundu.app.ui.adaptive.rememberIsExpandedLayout
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 应用三大页签。
 *
 * @property labelRes 页签文案资源 id。
 */
enum class JieYunDuTab(@StringRes val labelRes: Int) {
    /** 首页。 */
    HOME(R.string.tab_home),

    /** 下载。 */
    DOWNLOAD(R.string.tab_download),

    /** 设置。 */
    SETTINGS(R.string.tab_settings)
}

/**
 * 导航宿主。
 *
 * 说明：手写状态导航（不引入 `androidx.navigation`——未列入第五部分技术栈，D8）。
 * 平板 → 左侧竖直玻璃条；手机 → 顶部横向玻璃条。内容切换使用淡入淡出（[Crossfade]），
 * 与指示器弹簧分离（D6）。
 *
 * @param modifier 外部修饰符。
 */
@Composable
fun JieYunDuNavHost(modifier: Modifier = Modifier) {
    var selectedOrdinal by rememberSaveable { mutableStateOf(0) }
    val tabs = JieYunDuTab.entries
    val labels = tabs.map { stringResource(it.labelRes) }
    val onSelect: (Int) -> Unit = { index -> selectedOrdinal = index }
    val selectedTab = tabs[selectedOrdinal.coerceIn(0, tabs.lastIndex)]

    if (rememberIsExpandedLayout()) {
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail(
                labels = labels,
                selectedIndex = selectedOrdinal,
                onSelect = onSelect,
                modifier = Modifier.fillMaxHeight()
            )
            NavContent(tab = selectedTab, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            NavigationBar(
                labels = labels,
                selectedIndex = selectedOrdinal,
                onSelect = onSelect,
                modifier = Modifier.fillMaxWidth()
            )
            NavContent(tab = selectedTab, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/**
 * 页面内容占位（阶段 8 接入真实 Screen）。
 *
 * 说明：仅显示当前页签名，用于验证内容切换与指示器弹簧的分离（D6）。
 *
 * @param tab 当前页签。
 * @param modifier 外部修饰符。
 */
@Composable
private fun NavContent(tab: JieYunDuTab, modifier: Modifier = Modifier) {
    Crossfade(targetState = tab, modifier = modifier, label = "navContent") { current ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(current.labelRes),
                style = MaterialTheme.typography.headlineLarge,
                color = JieYunDuColors.TextPrimary
            )
        }
    }
}