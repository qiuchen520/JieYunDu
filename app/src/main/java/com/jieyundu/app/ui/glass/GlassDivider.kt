// 文件：GlassDivider.kt
// 职责：液态玻璃分隔线
// 依赖：Compose foundation / ui、JieYunDuColors、Dimens
// 协议：AGPL-3.0

package com.jieyundu.app.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 液态玻璃分隔线。
 *
 * 说明：横向渐变（透明 → 白 20% → 透明），1dp 厚。
 *
 * @param modifier 外部修饰符。
 */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.DividerThickness)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        JieYunDuColors.GlassHighlightClear,
                        JieYunDuColors.GlassDivider,
                        JieYunDuColors.GlassHighlightClear
                    )
                )
            )
    )
}
