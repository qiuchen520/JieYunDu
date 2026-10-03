// 文件：GlassBackground.kt
// 职责：液态玻璃背板——深色底 + 柔和彩色块 + 全局噪声
// 依赖：Compose foundation / ui、JieYunDuColors、Dimens
// 协议：AGPL-3.0

package com.jieyundu.app.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/** 背景色块透明度。 */
private const val BLOB_ALPHA = 0.55f

/** 噪声点半径（像素）。 */
private const val NOISE_DOT_RADIUS_PX = 0.5f

/**
 * 液态玻璃背板。
 *
 * 说明（9.1）：背板不是纯色，而是由多个柔和彩色块叠加构成，供上层玻璃面板采样；
 * 全局叠加一层极淡噪点（9.2）。
 *
 * @param modifier 外部修饰符。
 * @param content 背板之上的内容。
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JieYunDuColors.Background)
    ) {
        BackdropBlobs(modifier = Modifier.fillMaxSize())
        NoiseOverlay(modifier = Modifier.fillMaxSize())
        content()
    }
}

/**
 * 绘制三块柔和彩色背板光斑。
 *
 * @param modifier 外部修饰符。
 */
@Composable
private fun BackdropBlobs(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .offset(x = Dimens.BackdropBlobLargeOffset, y = Dimens.BackdropBlobLargeOffset)
                .size(Dimens.BackdropBlobLarge)
                .background(brush = blobBrush(JieYunDuColors.BackdropPurple))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = Dimens.BackdropBlobMediumOffset)
                .size(Dimens.BackdropBlobMedium)
                .background(brush = blobBrush(JieYunDuColors.BackdropBlue))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = Dimens.BackdropBlobSmallOffset)
                .size(Dimens.BackdropBlobSmall)
                .background(brush = blobBrush(JieYunDuColors.BackdropCyan))
        )
    }
}

/**
 * 构造单个色块的径向渐变画刷。
 *
 * @param color 色块颜色。
 * @return 由实色渐变到透明的径向画刷。
 */
private fun blobBrush(color: Color): Brush = Brush.radialGradient(
    colors = listOf(color.copy(alpha = BLOB_ALPHA), Color.Transparent)
)

/**
 * 全局噪声层（9.2：透明度 1.5%）。
 *
 * 说明：采用稀疏点阵近似均匀噪点，静态绘制，不随帧变化。
 *
 * @param modifier 外部修饰符。
 */
@Composable
private fun NoiseOverlay(modifier: Modifier = Modifier) {
    val spacingPx = with(LocalDensity.current) { Dimens.NoiseDotSpacing.toPx() }
    val dotColor = JieYunDuColors.Noise
    Canvas(modifier = modifier) {
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawCircle(color = dotColor, radius = NOISE_DOT_RADIUS_PX, center = Offset(x, y))
                x += spacingPx
            }
            y += spacingPx
        }
    }
}
