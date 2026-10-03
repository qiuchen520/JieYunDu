// 文件：GlassCard.kt
// 职责：液态玻璃卡片（可选点击 + 缩放回弹）
// 依赖：GlassPanel、Compose foundation / ui、Dimens
// 协议：AGPL-3.0

package com.jieyundu.app.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.jieyundu.app.ui.theme.Dimens

/** 卡片按下缩放比（9.6：点击时缩放 0.98）。 */
private const val CARD_PRESSED_SCALE = 0.98f

/** 卡片按下动画阻尼比。 */
private const val CARD_SPRING_DAMPING = 0.6f

/** 卡片按下动画刚度。 */
private const val CARD_SPRING_STIFFNESS = 420f

/**
 * 液态玻璃卡片。
 *
 * 说明（9.6）：卡片整体可点击时缩放 0.98 并回弹；不传入 [onClick] 时为纯展示卡片。
 *
 * @param modifier 外部修饰符。
 * @param cornerRadius 圆角半径。
 * @param contentPadding 内边距。
 * @param onClick 点击回调；为 null 时不可点击。
 * @param content 卡片内容。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Dimens.CardCorner,
    contentPadding: Dp = Dimens.PanelPadding,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    if (onClick == null) {
        GlassPanel(
            modifier = modifier,
            cornerRadius = cornerRadius,
            contentPadding = contentPadding,
            content = content
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) CARD_PRESSED_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = CARD_SPRING_DAMPING,
            stiffness = CARD_SPRING_STIFFNESS
        ),
        label = "glassCardPress"
    )
    GlassPanel(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        cornerRadius = cornerRadius,
        contentPadding = contentPadding,
        content = content
    )
}
