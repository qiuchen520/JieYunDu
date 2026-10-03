// 文件：Dimens.kt
// 职责：集中定义全部尺寸常量（C7：禁止硬编码 dp 值）
// 依赖：Compose UI unit
// 协议：AGPL-3.0

package com.jieyundu.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 尺寸常量表（对应编码风格 C7）。
 *
 * 说明：所有 dp 值必须引用此处常量，不得在 Composable 中硬编码。
 */
object Dimens {
    /** 超小间距。 */
    val SpaceXs: Dp = 4.dp

    /** 小间距。 */
    val SpaceSm: Dp = 8.dp

    /** 中等间距（手机卡片间距）。 */
    val SpaceMd: Dp = 12.dp

    /** 大间距。 */
    val SpaceLg: Dp = 16.dp

    /** 较大间距（平板卡片间距）。 */
    val SpaceXl: Dp = 20.dp

    /** 超大间距。 */
    val SpaceXxl: Dp = 24.dp

    /** 玻璃面板内边距（平板）。 */
    val PanelPadding: Dp = 24.dp

    /** 玻璃面板内边距（手机）。 */
    val PanelPaddingCompact: Dp = 16.dp

    /** 卡片圆角。 */
    val CardCorner: Dp = 24.dp

    /** 圆角下限（9.2 规定不得小于 16dp）。 */
    val MinCorner: Dp = 16.dp

    /** 按钮圆角。 */
    val ButtonCorner: Dp = 20.dp

    /** 按钮高度（平板）。 */
    val ButtonHeight: Dp = 56.dp

    /** 按钮高度（手机）。 */
    val ButtonHeightCompact: Dp = 48.dp

    /** 输入框高度（平板）。 */
    val InputHeight: Dp = 64.dp

    /** 输入框高度（手机）。 */
    val InputHeightCompact: Dp = 52.dp

    /** 分隔线厚度。 */
    val DividerThickness: Dp = 1.dp

    /** 边缘高光厚度。 */
    val HighlightStroke: Dp = 1.dp

    /** 顶部标题栏高度（平板）。 */
    val HeaderHeight: Dp = 72.dp

    /** 顶部标题栏高度（手机）。 */
    val HeaderHeightCompact: Dp = 64.dp

    /** 圆形图标按钮直径。 */
    val IconButtonSize: Dp = 48.dp

    /** 玻璃模糊半径（平板）。 */
    val GlassBlurRadius: Dp = 24.dp

    /** 玻璃模糊半径（手机）。 */
    val GlassBlurRadiusCompact: Dp = 16.dp

    /** 内侧阴影模糊半径。 */
    val InnerShadowBlur: Dp = 4.dp

    /** 背景色块尺寸（大）。 */
    val BackdropBlobLarge: Dp = 520.dp

    /** 背景色块尺寸（中）。 */
    val BackdropBlobMedium: Dp = 420.dp

    /** 背景色块尺寸（小）。 */
    val BackdropBlobSmall: Dp = 360.dp

    /** 背景色块偏移（大）。 */
    val BackdropBlobLargeOffset: Dp = (-120).dp

    /** 背景色块偏移（中）。 */
    val BackdropBlobMediumOffset: Dp = 80.dp

    /** 背景色块偏移（小）。 */
    val BackdropBlobSmallOffset: Dp = 260.dp

    /** 噪声点间距。 */
    val NoiseDotSpacing: Dp = 8.dp

    // --- 导航（阶段 7，第十部分；双方向玻璃条）---

    /** 横向导航条厚度（高度，10.2 手机）。 */
    val NavBarThicknessHorizontal: Dp = 64.dp

    /** 竖直导航条厚度（宽度，Owner 裁决 · 平板）。 */
    val NavBarThicknessVertical: Dp = 96.dp

    /** 横向导航条画面边距（10.8：宽度 = 屏宽 − 32dp → 每侧 16dp）。 */
    val NavBarLengthInsetHorizontal: Dp = 16.dp

    /** 指示器沿厚度方向的单侧留白（10.2：上下/左右各 4dp）。 */
    val NavIndicatorInset: Dp = 4.dp

    /** 指示器边缘高光厚度（10.2：1dp）。 */
    val NavIndicatorStroke: Dp = 1.dp
}