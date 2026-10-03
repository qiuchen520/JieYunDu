// 文件：GlassPanel.kt
// 职责：液态玻璃面板——半透明白底 + 边缘高光 + 内阴影
// 依赖：Compose foundation / ui、JieYunDuColors、Dimens
// 协议：AGPL-3.0

package com.jieyundu.app.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/** 内阴影竖向占比系数。 */
private const val INNER_SHADOW_BAND_FACTOR = 3f

/**
 * 液态玻璃面板。
 *
 * 说明（9.2）：白色 8% 底 + 白色 15% 边框 + 左上高光渐变线 + 底部内阴影。
 *
 * 注意：本实现为 §9.8 降级固化方案（用 Compose 原生绘制近似玻璃观感，
 * 不依赖 Cloudy 的背景模糊 API），详见阶段 6 交付说明。
 *
 * @param modifier 外部修饰符。
 * @param cornerRadius 圆角半径。
 * @param contentPadding 内边距。
 * @param content 面板内容。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Dimens.CardCorner,
    contentPadding: Dp = Dimens.PanelPadding,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color = JieYunDuColors.GlassFill, shape = shape)
            .drawWithContent {
                drawContent()
                drawEdgeHighlight(shape)
                drawInnerShadow(shape)
            }
            .border(width = Dimens.HighlightStroke, color = JieYunDuColors.GlassBorder, shape = shape)
            .padding(contentPadding),
        content = content
    )
}

/**
 * 沿面板轮廓绘制左上高光渐变线。
 *
 * @param shape 面板形状。
 */
private fun DrawScope.drawEdgeHighlight(shape: Shape) {
    val brush = Brush.linearGradient(
        colors = listOf(JieYunDuColors.GlassHighlightTop, JieYunDuColors.GlassHighlightClear),
        start = Offset.Zero,
        end = Offset(size.width, size.height)
    )
    drawOutline(
        outline = shape.createOutline(size, layoutDirection, this),
        brush = brush,
        style = Stroke(width = Dimens.HighlightStroke.toPx())
    )
}

/**
 * 沿面板轮廓绘制底部内阴影（竖向渐变近似）。
 *
 * @param shape 面板形状。
 */
private fun DrawScope.drawInnerShadow(shape: Shape) {
    val blur = Dimens.InnerShadowBlur.toPx()
    val brush = Brush.verticalGradient(
        colors = listOf(JieYunDuColors.GlassHighlightClear, JieYunDuColors.InnerShadow),
        startY = (size.height - blur * INNER_SHADOW_BAND_FACTOR).coerceAtLeast(0f),
        endY = size.height
    )
    drawOutline(
        outline = shape.createOutline(size, layoutDirection, this),
        brush = brush,
        style = Stroke(width = blur)
    )
}
