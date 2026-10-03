// 文件：LinkInputCard.kt
// 职责：首页输入区玻璃卡片（链接输入框 + 选填提取码 + 右侧自适应解析按钮）
// 依赖：GlassCard、GlassButton、BasicTextField、Dimens、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.home.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.jieyundu.app.R
import com.jieyundu.app.ui.glass.GlassButton
import com.jieyundu.app.ui.glass.GlassCard
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 输入区玻璃卡片（9.6.2 布局修订）。
 *
 * 说明：
 * - 链接输入框使用 Compose Foundation 的 [BasicTextField] 并自绘占位文字（R5 / D3）；
 * - 链接框右侧为宽度自适应的「解析」主色按钮（9.6.2）；
 * - 链接框下方为**选填**提取码输入框（Owner 布局修订：常驻展示，可不填）。
 *
 * @param input 链接输入文本。
 * @param onInputChange 链接变化回调。
 * @param code 提取码输入文本（选填）。
 * @param onCodeChange 提取码变化回调。
 * @param onParse 点击解析回调（解析中不触发）。
 * @param isParsing 是否正在解析（驱动按钮文案切换）。
 * @param modifier 外部修饰符。
 * @param isExpanded true 表示平板尺寸档位，false 表示手机档位。
 */
@Composable
fun LinkInputCard(
    input: String,
    onInputChange: (String) -> Unit,
    code: String = "",
    onCodeChange: (String) -> Unit = {},
    onParse: () -> Unit,
    isParsing: Boolean,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = true
) {
    val inputHeight: Dp = if (isExpanded) Dimens.InputHeight else Dimens.InputHeightCompact
    val buttonHeight: Dp = if (isExpanded) Dimens.ButtonHeight else Dimens.ButtonHeightCompact
    val inputCorner: Dp = if (isExpanded) Dimens.InputCorner else Dimens.InputCornerCompact
    val buttonCorner: Dp = if (isExpanded) Dimens.ButtonCorner else Dimens.ButtonCornerCompact

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Dimens.CardCorner,
        contentPadding = Dimens.InputCardPadding
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InputField(
                    value = input,
                    onValueChange = onInputChange,
                    hintRes = R.string.link_input_hint,
                    height = inputHeight,
                    corner = inputCorner,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(Dimens.SpaceLg))
                GlassButton(
                    text = stringResource(
                        if (isParsing) R.string.parse_parsing else R.string.action_parse
                    ),
                    onClick = { if (!isParsing) onParse() },
                    height = buttonHeight,
                    cornerRadius = buttonCorner,
                    fillColor = JieYunDuColors.ButtonFill,
                    borderColor = JieYunDuColors.ButtonBorder,
                    contentColor = JieYunDuColors.OnPrimary
                )
            }
            Spacer(modifier = Modifier.height(Dimens.SpaceMd))
            InputField(
                value = code,
                onValueChange = onCodeChange,
                hintRes = R.string.link_code_hint,
                height = inputHeight,
                corner = inputCorner,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 单行输入框（浅灰底 + 自绘占位文字）。
 *
 * @param value 文本值。
 * @param onValueChange 变化回调。
 * @param hintRes 占位文案资源 id。
 * @param height 高度。
 * @param corner 圆角。
 * @param modifier 外部修饰符。
 */
@Composable
private fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes hintRes: Int,
    height: Dp,
    corner: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(JieYunDuColors.InputFieldFill)
            .padding(horizontal = Dimens.SpaceLg),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = stringResource(hintRes),
                style = MaterialTheme.typography.bodyLarge,
                color = JieYunDuColors.TextTertiary
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = JieYunDuColors.TextPrimary
            ),
            cursorBrush = SolidColor(JieYunDuColors.TextPrimary),
            singleLine = true
        )
    }
}