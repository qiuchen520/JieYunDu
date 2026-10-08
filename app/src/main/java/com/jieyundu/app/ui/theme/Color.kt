// 文件：Color.kt
// 职责：集中定义全部颜色常量（C6：禁止硬编码十六进制）
// 依赖：Compose UI graphics
// 协议：AGPL-3.0

package com.jieyundu.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 颜色常量表（对应编码风格 C6）。
 *
 * 说明：2026-10-03 按 Owner 视觉基准整体由「深色玻璃」切换为「浅色 Area 风」——
 * 极浅灰底 + 白色卡片 + 蓝色主色。常量为同名改值，引用方无需改名。
 * 所有颜色必须引用此处常量，不得在 Composable 中硬编码十六进制。
 */
object JieYunDuColors {
    /** 页面底色（浅灰 #F5F5F7）。 */
    val Background: Color = Color(0xFFF5F5F7)

    /** 背板光斑：淡紫蓝。 */
    val BackdropPurple: Color = Color(0xFF9BB0FF)

    /** 背板光斑：主色蓝。 */
    val BackdropBlue: Color = Color(0xFF4A6CF7)

    /** 背板光斑：淡蓝。 */
    val BackdropCyan: Color = Color(0xFFBFD0FF)

    /** 玻璃 / 卡片底色（白色 95%，保留轻微玻璃通透感）。 */
    val GlassFill: Color = Color(0xF2FFFFFF)

    /** 实心白（按钮等强调元件）。 */
    val GlassFillStrong: Color = Color(0xFFFFFFFF)

    /** 卡片 / 控件边框（浅灰 #E5E5EA）。 */
    val GlassBorder: Color = Color(0xFFE5E5EA)

    /** 边缘高光起始（白色 40%，浅色下仅作极淡顶部光泽）。 */
    val GlassHighlightTop: Color = Color(0x66FFFFFF)

    /** 高光 / 描边渐变的透明端。 */
    val GlassHighlightClear: Color = Color(0x00FFFFFF)

    /** 分隔线（浅灰 #E5E5EA）。 */
    val GlassDivider: Color = Color(0xFFE5E5EA)

    /** 内侧阴影（黑色 4%，极淡）。 */
    val InnerShadow: Color = Color(0x0A000000)

    /** 全局噪声（黑色 1.5%，浅色底上用暗噪点）。 */
    val Noise: Color = Color(0x04000000)

    /** 卡片外阴影（黑色 4%）。 */
    val Shadow: Color = Color(0x0A000000)

    /** 主文字（近黑 #1C1C1E）。 */
    val TextPrimary: Color = Color(0xFF1C1C1E)

    /** 次级文字（灰 #8E8E93）。 */
    val TextSecondary: Color = Color(0xFF8E8E93)

    /** 三级文字（更浅灰 #AEAEB2，占位 / 次要）。 */
    val TextTertiary: Color = Color(0xFFAEAEB2)

    /** 进度条底色（浅灰 #E5E5EA）。 */
    val ProgressTrack: Color = Color(0xFFE5E5EA)

    /** 进度条填充（主色蓝 #4A6CF7）。 */
    val ProgressFill: Color = Color(0xFF4A6CF7)

    /** 导航指示器底色（主色浅底 #E8EEFF）。 */
    val NavIndicatorFill: Color = Color(0xFFE8EEFF)

    /** 指示器边缘高光起始（白色 20%，浅色下极淡）。 */
    val NavIndicatorHighlight: Color = Color(0x33FFFFFF)

    /** 指示器内阴影（黑色 4%，极淡）。 */
    val NavIndicatorInnerShadow: Color = Color(0x0A000000)

    // --- 主界面（阶段 8，第九部分）---

    /** 主色（Area 蓝 #4A6CF7）。 */
    val Primary: Color = Color(0xFF4A6CF7)

    /** 主色浅底（选中态 / 图标底 #E8EEFF）。 */
    val PrimaryLight: Color = Color(0xFFE8EEFF)

    /** 主色之上的文字 / 图标（白色）。 */
    val OnPrimary: Color = Color(0xFFFFFFFF)

    /** 主按钮底色（主色蓝）。 */
    val ButtonFill: Color = Color(0xFF4A6CF7)

    /** 主按钮边框（与底色同色，无可见描边）。 */
    val ButtonBorder: Color = Color(0xFF4A6CF7)

    /** 圆形图标按钮底色（主色浅底 #E8EEFF）。 */
    val IconButtonFill: Color = Color(0xFFE8EEFF)

    /** 输入框底色（浅灰 #F2F2F7，与白卡区分）。 */
    val InputFieldFill: Color = Color(0xFFF2F2F7)

    /** 次级强调文字（近黑，列表文件名）。 */
    val TextMuted: Color = Color(0xFF1C1C1E)

    /** 弱化文字（灰，速度 / 大小）。 */
    val TextFaint: Color = Color(0xFF8E8E93)

    // --- 阶段 8 整改（UI 重构）---

    /** 空态灰色图标（浅灰 #C7C7CC）。 */
    val EmptyStateIcon: Color = Color(0xFFC7C7CC)


}
