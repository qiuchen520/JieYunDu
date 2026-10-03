// 文件：JieYunDuNavHost.kt
// 职责：导航宿主——按窗口尺寸选取导航形式，承载四大页面
// 依赖：WindowSizeHelper、NavigationRail、NavigationBar、四个 Screen、Compose runtime/foundation
// 协议：AGPL-3.0
//
// 变更记录（【修订 JYD-UI-2026-10-03】）：原「顶部标题栏」与导航条功能重复、浪费竖向空间，
// 按 Owner 要求整体删除；设置入口统一由导航条最后一个「设置」页签承载。原先私有的
// `AppTitleBar` / `SettingsGlyph` 及其几何常量已随之移除，内容区自然上移。

package com.jieyundu.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.jieyundu.app.R
import com.jieyundu.app.ui.adaptive.rememberIsExpandedLayout
import com.jieyundu.app.ui.screens.download.DownloadScreen
import com.jieyundu.app.ui.screens.home.HomeScreen
import com.jieyundu.app.ui.screens.login.NetdiskBrowserScreen
import com.jieyundu.app.ui.screens.login.NetdiskBrowserViewModel
import com.jieyundu.app.ui.screens.login.NetdiskLoginViewModel
import com.jieyundu.app.ui.screens.login.NetdiskPickerScreen
import com.jieyundu.app.ui.screens.login.WebViewLoginScreen
import com.jieyundu.app.ui.screens.settings.SettingsScreen

/**
 * 应用四大页签。
 *
 * @property labelRes 页签文案资源 id。
 */
enum class JieYunDuTab(@StringRes val labelRes: Int) {
    /** 首页。 */
    HOME(R.string.tab_home),

    /** 网盘（登录入口）。 */
    NETDISK(R.string.tab_netdisk),

    /** 下载。 */
    DOWNLOAD(R.string.tab_download),

    /** 设置。 */
    SETTINGS(R.string.tab_settings)
}

/**
 * 导航宿主。
 *
 * 说明：手写状态导航（不引入 `androidx.navigation`——未列入第五部分技术栈，D8）。
 * 平板 → 左侧竖直玻璃条；手机 → 顶部横向玻璃条（第一层），内容区渲染在其下方。
 * 内容切换使用淡入淡出（[Crossfade]），与指示器弹簧分离（D6）。
 *
 * @param modifier 外部修饰符。
 * @param preset 弹簧预设（阶段 7 固定为 [JellyPreset]，透传至导航 wrapper）。
 */
@Composable
fun JieYunDuNavHost(
    modifier: Modifier = Modifier,
    preset: NavSpringPreset = JellyPreset
) {
    var selectedOrdinal by rememberSaveable { mutableStateOf(0) }
    val tabs = JieYunDuTab.entries
    val labels = tabs.map { stringResource(it.labelRes) }
    val onSelect: (Int) -> Unit = { index -> selectedOrdinal = index }
    val selectedTab = tabs[selectedOrdinal.coerceIn(0, tabs.lastIndex)]
    val loginViewModel: NetdiskLoginViewModel = hiltViewModel()

    if (rememberIsExpandedLayout()) {
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail(
                labels = labels,
                selectedIndex = selectedOrdinal,
                onSelect = onSelect,
                modifier = Modifier.fillMaxHeight(),
                preset = preset
            )
            NavContent(
                tab = selectedTab,
                loginViewModel = loginViewModel,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            NavigationBar(
                labels = labels,
                selectedIndex = selectedOrdinal,
                onSelect = onSelect,
                modifier = Modifier.fillMaxWidth(),
                preset = preset
            )
            NavContent(
                tab = selectedTab,
                loginViewModel = loginViewModel,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

/**
 * 页面内容宿主。
 *
 * 说明：「网盘」页需承载两级状态——网盘选择页与内嵌 WebView 登录页。二者由
 * [NetdiskLoginViewModel.loginTarget] 驱动：非空即显示登录页，登录成功/关闭后回到选择页。
 *
 * @param tab 当前页签。
 * @param loginViewModel 网盘登录状态 ViewModel。
 * @param modifier 外部修饰符。
 */
@Composable
private fun NavContent(
    tab: JieYunDuTab,
    loginViewModel: NetdiskLoginViewModel,
    modifier: Modifier = Modifier
) {
    Crossfade(targetState = tab, modifier = modifier, label = "navContent") { current ->
        when (current) {
            JieYunDuTab.HOME -> HomeScreen(modifier = Modifier.fillMaxSize())
            JieYunDuTab.NETDISK -> NetdiskSection(loginViewModel = loginViewModel)
            JieYunDuTab.DOWNLOAD -> DownloadScreen(modifier = Modifier.fillMaxSize())
            JieYunDuTab.SETTINGS -> SettingsScreen(modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * 网盘页内容：登录页与选择页二选一。
 *
 * @param loginViewModel 网盘登录状态 ViewModel。
 */
@Composable
private fun NetdiskSection(loginViewModel: NetdiskLoginViewModel) {
    val loginTarget by loginViewModel.loginTarget.collectAsState()
    val loggedIn by loginViewModel.loggedIn.collectAsState()
    val browserViewModel: NetdiskBrowserViewModel = hiltViewModel()
    val browserState by browserViewModel.uiState.collectAsState()
    val target = loginTarget
    when {
        target != null -> {
            WebViewLoginScreen(
                viewModel = loginViewModel,
                type = target,
                modifier = Modifier.fillMaxSize()
            )
        }

        browserState.open -> {
            NetdiskBrowserScreen(
                state = browserState,
                onOpenFolder = browserViewModel::openFolder,
                onNavigateUp = browserViewModel::navigateUp,
                onClose = browserViewModel::close,
                modifier = Modifier.fillMaxSize()
            )
        }

        else -> {
            NetdiskPickerScreen(
                modifier = Modifier.fillMaxSize(),
                loggedInTypes = loggedIn,
                onLogin = loginViewModel::startLogin,
                onLogout = loginViewModel::logout,
                onOpenNetdisk = browserViewModel::open
            )
        }
    }
}
