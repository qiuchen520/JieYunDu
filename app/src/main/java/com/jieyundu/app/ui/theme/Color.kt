// 文件：Color.kt
// 职责：集中定义全部颜色常量（C6：禁止硬编码十六进制）
// 依赖：Compose UI graphics
// 协议：AGPL-3.0

package com.jieyundu.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 颜色常量表（对应编码风格 C6）。
 *
 * 说明：所有颜色必须引用此处常量，不得在 Composable 中硬编码十六进制。
 */
object JieYunDuColors {
    /** 背板深色底色（9.1：近黑）。 */
    val Background: Color = Color(0xFF0A0A0F)

    /** 背景色块：紫。 */
    val BackdropPurple: Color = Color(0xFF6C4CE0)

    /** 背景色块：蓝。 */
    val BackdropBlue: Color = Color(0xFF2F6BFF)

    /** 背景色块：青。 */
    val BackdropCyan: Color = Color(0xFF36D6E0)

    /** 玻璃面板底色（白色 8%）。 */
    val GlassFill: Color = Color(0x14FFFFFF)

    /** 玻璃面板底色（白色 12%，按钮等强调元件）。 */
    val GlassFillStrong: Color = Color(0x1FFFFFFF)

    /** 玻璃面板边框（白色 15%）。 */
    val GlassBorder: Color = Color(0x26FFFFFF)

    /** 边缘高光起始（白色 60%）。 */
    val GlassHighlightTop: Color = Color(0x99FFFFFF)

    /** 高光 / 描边渐变的透明端。 */
    val GlassHighlightClear: Color = Color(0x00FFFFFF)

    /** 分隔线主色（白色 20%）。 */
    val GlassDivider: Color = Color(0x33FFFFFF)

    /** 内侧阴影（黑色 8%）。 */
    val InnerShadow: Color = Color(0x14000000)

    /** 全局噪声（白色 1.5%）。 */
    val Noise: Color = Color(0x04FFFFFF)

    /** 主文字（白色 95%）。 */
    val TextPrimary: Color = Color(0xF2FFFFFF)

    /** 次级文字（白色 60%）。 */
    val TextSecondary: Color = Color(0x99FFFFFF)

    /** 三级文字（白色 55%）。 */
    val TextTertiary: Color = Color(0x8CFFFFFF)

    /** 进度条底色（白色 10%）。 */
    val ProgressTrack: Color = Color(0x1AFFFFFF)

    /** 进度条填充（白色 70%）。 */
    val ProgressFill: Color = Color(0xB3FFFFFF)

    /** 导航指示器底色（白色 18%，10.2）。 */
    val NavIndicatorFill: Color = Color(0x2EFFFFFF)

    /** 指示器边缘高光起始（白色 50%，10.2）。 */
    val NavIndicatorHighlight: Color = Color(0x80FFFFFF)

    /** 指示器内阴影（黑色 10%，10.2：画在指示器内侧底部）。 */
    val NavIndicatorInnerShadow: Color = Color(0x1A000000)

    // --- 主界面（阶段 8，第九部分）---

    /** 强调按钮底色（白色 15%，9.6.2 解析按钮）。 */
    val ButtonFill: Color = Color(0x26FFFFFF)

    /** 强调按钮边框（白色 30%，9.6.2）。 */
    val ButtonBorder: Color = Color(0x4DFFFFFF)

    /** 圆形图标按钮底色（白色 20%，9.6.3 下载按钮）。 */
    val IconButtonFill: Color = Color(0x33FFFFFF)

    /** 次级强调文字（白色 70%，9.6.4 列表文件名 / 进度文字）。 */
    val TextMuted: Color = Color(0xB3FFFFFF)

    /** 弱化文字（白色 50%，9.6.4 速度文字）。 */
    val TextFaint: Color = Color(0x80FFFFFF)

    // --- 阶段 8 整改（UI 重构）---

    /** 空态灰色图标（白色 25%）。 */
    val EmptyStateIcon: Color = Color(0x40FFFFFF)

    /** 玻璃折射渐变：紫色端（低透明，模拟透出背板）。 */
    val GlassRefractionPurple: Color = Color(0x336C4CE0)

    /** 玻璃折射渐变：蓝色端（低透明，模拟透出背板）。 */
    val GlassRefractionBlue: Color = Color(0x262F6BFF)
}