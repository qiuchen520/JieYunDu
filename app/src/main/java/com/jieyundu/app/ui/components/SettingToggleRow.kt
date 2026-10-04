// 文件：SettingToggleRow.kt
// 职责：玻璃质感开关行（自绘开关，C2 设置项用）
// 依赖：GlassPanel、Dimens、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jieyundu.app.ui.glass.GlassPanel
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/** 开关轨道宽度。 */
private val ToggleTrackWidth = 48.dp

/** 开关轨道高度。 */
private val ToggleTrackHeight = 26.dp

/** 开关滑块直径。 */
private val ToggleKnobSize = 20.dp

/** 滑块与轨道边缘的间隙。 */
private val ToggleKnobGap = 3.dp

/**
 * 玻璃质感开关行（B2 整页玻璃化 + C2 设置项）。
 *
 * 视觉（不引入新颜色、不使用 Material 默认 Switch）：
 * - 整行是玻璃面板（半透明白底 + 边缘高光 + 极淡内阴影）；
 * - 开关自绘：开启时轨道为主色蓝、滑块白；关闭时轨道为 [JieYunDuColors.ProgressTrack]、滑块白；
 * - 整行可点，点击即切换。
 *
 * 说明：用自绘而非 `androidx.compose.material3.Switch`，遵循《要求.md》9.9
 * 「不要用 Material 默认组件直接堆叠」的既有约定，同时保持玻璃质感一致。
 *
 * @param title 开关标题。
 * @param description 开关说明（可为 null）。
 * @param checked 当前是否开启。
 * @param onCheckedChange 切换回调。
 * @param modifier 外部修饰符。
 * @param contentPadding 内边距。
 */
@Composable
fun SettingToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: Dp = Dimens.SpaceLg
) {
    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        cornerRadius = Dimens.CardCorner,
        contentPadding = contentPadding
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = JieYunDuColors.TextPrimary
                )
                if (description != null) {
                    Spacer(modifier = Modifier.height(Dimens.SpaceXs))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelMedium,
                        color = JieYunDuColors.TextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(Dimens.SpaceLg))
            GlassToggle(checked = checked)
        }
    }
}

/**
 * 自绘玻璃开关。
 *
 * @param checked 是否开启。
 */
@Composable
private fun GlassToggle(checked: Boolean) {
    val knobOffset by animateDpAsState(
        targetValue = if (checked) {
            ToggleTrackWidth - ToggleKnobSize - ToggleKnobGap
        } else {
            ToggleKnobGap
        },
        label = "settingToggleKnob"
    )
    Box(
        modifier = Modifier
            .width(ToggleTrackWidth)
            .height(ToggleTrackHeight)
            .clip(RoundedCornerShape(ToggleTrackHeight / 2))
            .background(if (checked) JieYunDuColors.Primary else JieYunDuColors.ProgressTrack),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(ToggleKnobSize)
                .clip(CircleShape)
                .background(JieYunDuColors.GlassFillStrong)
        )
    }
}
