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

    /** 输入区玻璃卡片内边距（9.6.2：20dp）。 */
    val InputCardPadding: Dp = 20.dp

    /** 卡片外阴影高度（浅色 Area 风：极淡投影，模糊约 8dp、下偏 2dp）。 */
    val CardElevation: Dp = 6.dp

    /** 卡片圆角（浅色 Area 风：16dp）。 */
    val CardCorner: Dp = 16.dp

    /** 圆角下限（9.2 规定不得小于 16dp）。 */
    val MinCorner: Dp = 16.dp

    /** 按钮圆角（平板，≈ 高度 56dp × 0.33）。 */
    val ButtonCorner: Dp = 18.dp

    /** 按钮圆角（手机，≈ 高度 48dp × 0.33）。 */
    val ButtonCornerCompact: Dp = 16.dp

    /** 输入框圆角（平板，≈ 高度 64dp × 0.33）。 */
    val InputCorner: Dp = 21.dp

    /** 输入框圆角（手机，≈ 高度 52dp × 0.33）。 */
    val InputCornerCompact: Dp = 17.dp

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

    // --- 主界面（阶段 8，第九部分）---

    /** 解析结果卡片右侧圆形下载按钮直径（9.6.3：56dp）。 */
    val DownloadButtonSize: Dp = 56.dp

    /** 下载列表项高度（平板，9.6.4：88dp）。 */
    val DownloadItemHeight: Dp = 88.dp

    /** 下载列表项高度（手机，9.6.4：76dp）。 */
    val DownloadItemHeightCompact: Dp = 76.dp

    /** 进度条高度（9.6.4：6dp）。 */
    val ProgressBarHeight: Dp = 6.dp

    /** 进度条圆角（9.6.4：3dp；组件明文规格，优先于 9.2 的 16dp 下限）。 */
    val ProgressBarCorner: Dp = 3.dp

    // --- 阶段 8 整改（UI 重构）---

    /** 主内容区顶部内边距（平板整改：内容靠上、不贴顶）。 */
    val ContentTopPadding: Dp = 32.dp

    /** 悬浮标题栏左右画面边距（整改：左右各留 24dp）。 */
    val TitleBarMargin: Dp = 24.dp

    /** 标题栏圆角（平板，≈ 高度 72dp × 0.33）。 */
    val TitleBarCorner: Dp = 24.dp

    /** 标题栏圆角（手机，≈ 高度 64dp × 0.33）。 */
    val TitleBarCornerCompact: Dp = 21.dp

    /** 空态图标边长（下载列表空态）。 */
    val EmptyIconSize: Dp = 64.dp

    /** 背板光斑模糊半径（API 31+ 生效，低版本自动忽略）。 */
    val BackdropBlurRadius: Dp = 48.dp

    // --- 阶段 8 布局修订（首页 / 下载页 / 网盘页）---

    /** 下载筛选条高度。 */
    val FilterBarHeight: Dp = 44.dp

    /** 筛选胶囊高度。 */
    val FilterChipHeight: Dp = 36.dp

    /** 筛选胶囊圆角（≈ 高度 × 0.33）。 */
    val FilterChipCorner: Dp = 12.dp

    /** 文件勾选框边长。 */
    val CheckboxSize: Dp = 20.dp

    /** 列表项删除按钮直径。 */
    val DeleteButtonSize: Dp = 40.dp
}