// 文件：LinkInputCard.kt
// 职责：首页输入区玻璃卡片（无边框输入框 + 全宽解析按钮）
// 依赖：GlassCard、GlassButton、BasicTextField、Dimens、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.jieyundu.app.R
import com.jieyundu.app.ui.glass.GlassButton
import com.jieyundu.app.ui.glass.GlassCard
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 输入区玻璃卡片（9.6.2）。
 *
 * 说明：
 * - 输入框使用 Compose Foundation 的 [BasicTextField] 并自绘占位文字，
 *   不使用 Material 默认样式的 TextField（R5 / D3）；
 * - 按钮复用 [GlassButton]，按 9.6.2 使用白色 15% 底 + 白色 30% 边框。
 *
 * @param input 当前输入文本。
 * @param onInputChange 输入变化回调。
 * @param onParse 点击解析回调（解析中不触发）。
 * @param isParsing 是否正在解析（驱动按钮文案切换）。
 * @param modifier 外部修饰符。
 * @param isExpanded true 表示平板尺寸档位，false 表示手机档位。
 */
@Composable
fun LinkInputCard(
    input: String,
    onInputChange: (String) -> Unit,
    onParse: () -> Unit,
    isParsing: Boolean,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = true
) {
    val inputHeight: Dp = if (isExpanded) Dimens.InputHeight else Dimens.InputHeightCompact
    val buttonHeight: Dp = if (isExpanded) Dimens.ButtonHeight else Dimens.ButtonHeightCompact
    val contentPadding: Dp =
        if (isExpanded) Dimens.PanelPadding else Dimens.PanelPaddingCompact

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Dimens.CardCorner,
        contentPadding = contentPadding
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(inputHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                if (input.isEmpty()) {
                    Text(
                        text = stringResource(R.string.link_input_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = JieYunDuColors.TextTertiary
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = JieYunDuColors.TextPrimary
                    ),
                    cursorBrush = SolidColor(JieYunDuColors.TextPrimary),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(Dimens.SpaceLg))
            GlassButton(
                text = stringResource(
                    if (isParsing) R.string.parse_parsing else R.string.action_parse
                ),
                onClick = { if (!isParsing) onParse() },
                modifier = Modifier.fillMaxWidth(),
                height = buttonHeight,
                cornerRadius = Dimens.ButtonCorner,
                fillColor = JieYunDuColors.ButtonFill,
                borderColor = JieYunDuColors.ButtonBorder
            )
        }
    }
}
