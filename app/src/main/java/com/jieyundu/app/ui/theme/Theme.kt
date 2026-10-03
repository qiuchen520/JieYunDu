// 文件：Theme.kt
// 职责：Compose 主题入口，装配颜色 / 字体 / 形状
// 依赖：Material3、JieYunDuColors、JieYunDuTypography、JieYunDuShapes
// 协议：AGPL-3.0

package com.jieyundu.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * 极云渡主题（深色 + 液态玻璃）。
 *
 * 说明：只提供色彩 / 字体 / 形状令牌，不提供任何 Material 默认组件样式（R5 / D3）。
 *
 * @param content 主题内的内容。
 */
@Composable
fun JieYunDuTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = JieYunDuColors.TextPrimary,
        onPrimary = JieYunDuColors.Background,
        background = JieYunDuColors.Background,
        onBackground = JieYunDuColors.TextPrimary,
        surface = JieYunDuColors.Background,
        onSurface = JieYunDuColors.TextPrimary,
        surfaceVariant = JieYunDuColors.GlassFill,
        onSurfaceVariant = JieYunDuColors.TextSecondary
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = JieYunDuTypography,
        shapes = JieYunDuShapes,
        content = content
    )
}
