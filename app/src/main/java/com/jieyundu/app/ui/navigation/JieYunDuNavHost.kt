// 文件：JieYunDuNavHost.kt
// 职责：导航宿主——按窗口尺寸选取导航形式，承载顶部标题栏与四大页面
// 依赖：WindowSizeHelper、NavigationRail、NavigationBar、四个 Screen、Compose runtime/foundation
// 协议：AGPL-3.0

package com.jieyundu.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import com.jieyundu.app.R
import com.jieyundu.app.ui.adaptive.rememberIsExpandedLayout
import com.jieyundu.app.ui.glass.GlassPanel
import com.jieyundu.app.ui.screens.download.DownloadScreen
import com.jieyundu.app.ui.screens.home.HomeScreen
import com.jieyundu.app.ui.screens.login.NetdiskPickerScreen
import com.jieyundu.app.ui.screens.settings.SettingsScreen
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
 * 平板 → 左侧竖直玻璃条；手机 → 顶部横向玻璃条；两者均在内容区上方渲染玻璃标题栏（9.6.1）。
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
    val onOpenSettings: () -> Unit = { selectedOrdinal = JieYunDuTab.SETTINGS.ordinal }

    if (rememberIsExpandedLayout()) {
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail(
                labels = labels,
                selectedIndex = selectedOrdinal,
                onSelect = onSelect,
                modifier = Modifier.fillMaxHeight(),
                preset = preset
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AppTitleBar(onOpenSettings = onOpenSettings)
                NavContent(tab = selectedTab, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            AppTitleBar(onOpenSettings = onOpenSettings)
            NavigationBar(
                labels = labels,
                selectedIndex = selectedOrdinal,
                onSelect = onSelect,
                modifier = Modifier.fillMaxWidth(),
                preset = preset
            )
            NavContent(tab = selectedTab, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/**
 * 顶部标题栏（9.6.1）：左侧应用名，右侧圆形玻璃设置按钮。
 *
 * @param onOpenSettings 点击设置按钮的回调（切换到「设置」页签）。
 * @param modifier 外部修饰符。
 */
@Composable
private fun AppTitleBar(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpanded = rememberIsExpandedLayout()
    val barHeight = if (isExpanded) Dimens.HeaderHeight else Dimens.HeaderHeightCompact
    val barCorner = if (isExpanded) Dimens.TitleBarCorner else Dimens.TitleBarCornerCompact

    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.TitleBarMargin, vertical = Dimens.SpaceSm)
            .height(barHeight),
        cornerRadius = barCorner,
        contentPadding = Dimens.SpaceSm
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = JieYunDuColors.TextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(Dimens.IconButtonSize)
                    .clip(CircleShape)
                    .background(JieYunDuColors.IconButtonFill)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) {
                SettingsGlyph(modifier = Modifier.size(Dimens.SpaceXxl))
            }
        }
    }
}

/**
 * 自绘「齿轮」设置图标。
 *
 * 说明（D8）：不引入 material-icons-extended（不在第五部分技术栈内），
 * 以 Compose 原生 [Canvas] 绘制近似齿轮，避免额外依赖。
 *
 * @param modifier 外部修饰符。
 */
@Composable
private fun SettingsGlyph(modifier: Modifier = Modifier) {
    val color = JieYunDuColors.Primary
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * GLYPH_STROKE_RATIO
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = color,
            radius = size.minDimension * GLYPH_RING_RADIUS_RATIO,
            center = center,
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = color,
            radius = size.minDimension * GLYPH_INNER_RADIUS_RATIO,
            center = center,
            style = Stroke(width = stroke)
        )
        val toothOuter = size.minDimension * GLYPH_TOOTH_OUTER_RATIO
        val toothInner = size.minDimension * GLYPH_TOOTH_INNER_RATIO
        repeat(GLYPH_TOOTH_COUNT) { index ->
            val angle =
                (index.toDouble() * 2.0 * PI / GLYPH_TOOTH_COUNT.toDouble()).toFloat()
            drawLine(
                color = color,
                start = Offset(
                    center.x + cos(angle) * toothInner,
                    center.y + sin(angle) * toothInner
                ),
                end = Offset(
                    center.x + cos(angle) * toothOuter,
                    center.y + sin(angle) * toothOuter
                ),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 页面内容宿主。
 *
 * @param tab 当前页签。
 * @param modifier 外部修饰符。
 */
@Composable
private fun NavContent(tab: JieYunDuTab, modifier: Modifier = Modifier) {
    Crossfade(targetState = tab, modifier = modifier, label = "navContent") { current ->
        when (current) {
            JieYunDuTab.HOME -> HomeScreen(modifier = Modifier.fillMaxSize())
            JieYunDuTab.NETDISK -> NetdiskPickerScreen(modifier = Modifier.fillMaxSize())
            JieYunDuTab.DOWNLOAD -> DownloadScreen(modifier = Modifier.fillMaxSize())
            JieYunDuTab.SETTINGS -> SettingsScreen(modifier = Modifier.fillMaxSize())
        }
    }
}

/** 图标线宽相对短边比例。 */
private const val GLYPH_STROKE_RATIO = 0.09f

/** 齿轮外圈半径相对短边比例。 */
private const val GLYPH_RING_RADIUS_RATIO = 0.34f

/** 齿轮内圈半径相对短边比例。 */
private const val GLYPH_INNER_RADIUS_RATIO = 0.14f

/** 齿顶点半径相对短边比例。 */
private const val GLYPH_TOOTH_OUTER_RATIO = 0.48f

/** 齿根半径相对短边比例。 */
private const val GLYPH_TOOTH_INNER_RATIO = 0.32f

/** 齿数。 */
private const val GLYPH_TOOTH_COUNT = 8