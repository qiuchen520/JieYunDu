// 文件：Theme.kt
// 职责：Compose 主题入口，装配颜色 / 字体 / 形状
// 依赖：Material3、JieYunDuColors、JieYunDuTypography、JieYunDuShapes
// 协议：AGPL-3.0

package com.jieyundu.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 极云渡主题（浅色 Area 风 + 玻璃质感）。
 *
 * 说明：2026-10-03 按 Owner 视觉基准，由深色方案整体切换为**浅色方案**
 * （浅灰底 + 白卡 + 蓝色主色）。只提供色彩 / 字体 / 形状令牌，
 * 不提供任何 Material 默认组件样式（R5 / D3）。
 *
 * @param content 主题内的内容。
 */
@Composable
fun JieYunDuTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = JieYunDuColors.Primary,
        onPrimary = JieYunDuColors.OnPrimary,
        background = JieYunDuColors.Background,
        onBackground = JieYunDuColors.TextPrimary,
        surface = JieYunDuColors.GlassFillStrong,
        onSurface = JieYunDuColors.TextPrimary,
        surfaceVariant = JieYunDuColors.PrimaryLight,
        onSurfaceVariant = JieYunDuColors.TextSecondary
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = JieYunDuTypography,
        shapes = JieYunDuShapes,
        content = content
    )
}