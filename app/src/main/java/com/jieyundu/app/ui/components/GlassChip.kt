// 文件：GlassChip.kt
// 职责：玻璃质感档位胶囊（下载页筛选 / 设置页档位共用）
// 依赖：GlassPanel、JieYunDuColors、Dimens
// 协议：AGPL-3.0

package com.jieyundu.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jieyundu.app.ui.glass.GlassPanel
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 玻璃质感档位胶囊（B2 整页玻璃化）。
 *
 * 视觉（不改配色、不改圆角）：
 * - 未选中：玻璃面板（半透明白底 + 边缘高光 + 极淡内阴影）+ 次级文字色；
 * - 选中：仍是主色蓝底 + 白字（不改配色），但改用 [GlassPanel]，保留玻璃的边缘高光与
 *   内阴影质感，不再是纯色扁平块。
 *
 * 说明：圆角沿用既有 [Dimens.FilterChipCorner]（≈ 高度 × 0.33，非全圆，符合 9.2 / 9.6.4）。
 *
 * @param label 胶囊文案。
 * @param selected 是否选中。
 * @param onClick 点击回调。
 * @param modifier 外部修饰符。
 */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        modifier = modifier
            .height(Dimens.FilterChipHeight)
            .clickable(onClick = onClick),
        cornerRadius = Dimens.FilterChipCorner,
        contentPadding = 0.dp,
        fillColor = if (selected) JieYunDuColors.Primary else JieYunDuColors.InputFieldFill,
        borderColor = if (selected) JieYunDuColors.ButtonBorder else JieYunDuColors.GlassBorder
    ) {
        Box(
            modifier = Modifier
                .height(Dimens.FilterChipHeight)
                .padding(horizontal = Dimens.SpaceLg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) JieYunDuColors.OnPrimary else JieYunDuColors.TextSecondary,
                maxLines = 1
            )
        }
    }
}
