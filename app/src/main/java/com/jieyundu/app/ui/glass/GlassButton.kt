// 文件：GlassButton.kt
// 职责：液态玻璃按钮（无 Material 默认样式）
// 依赖：Compose foundation / ui、JieYunDuColors、Dimens
// 协议：AGPL-3.0

package com.jieyundu.app.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/** 按下时缩放比（9.7：按钮点击缩放 0.96）。 */
private const val PRESSED_SCALE = 0.96f

/** 按下动画阻尼比（10.3 按下缩放参数）。 */
private const val PRESS_SPRING_DAMPING = 0.4f

/** 按下动画刚度。 */
private const val PRESS_SPRING_STIFFNESS = 800f

/** 禁用态整体透明度（【JYD-XUNLEI-P1B-2026-10-08】：登录页需要「不可点」态）。 */
private const val DISABLED_ALPHA = 0.45f

/**
 * 液态玻璃按钮。
 *
 * 说明：用 Box + 自定义 Modifier 实现（R5 / D3），不使用 Material Button。
 *
 * @param text 按钮文字（来自 strings.xml，禁止硬编码中文）。
 * @param onClick 点击回调。
 * @param modifier 外部修饰符。
 * @param enabled 是否可点击；false 时置灰（透明度 0.45）且不响应点击。
 * @param height 按钮高度。
 * @param cornerRadius 圆角半径。
 * @param fillColor 底色；默认白色实心底（浅色 Area 风）。
 * @param borderColor 边框色；默认浅灰描边。
 * @param contentColor 文字颜色；默认主文字色，主色按钮传入白色。
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Dimens.ButtonHeight,
    cornerRadius: Dp = Dimens.ButtonCorner,
    fillColor: Color = JieYunDuColors.GlassFillStrong,
    borderColor: Color = JieYunDuColors.GlassBorder,
    contentColor: Color = JieYunDuColors.TextPrimary
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = PRESS_SPRING_DAMPING,
            stiffness = PRESS_SPRING_STIFFNESS
        ),
        label = "glassButtonPress"
    )
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else DISABLED_ALPHA
            }
            .clip(shape)
            .background(color = fillColor, shape = shape)
            .border(width = Dimens.HighlightStroke, color = borderColor, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = Dimens.SpaceXl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
    }
}