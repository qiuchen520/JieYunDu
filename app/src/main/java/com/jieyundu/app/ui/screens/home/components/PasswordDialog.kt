// 文件：PasswordDialog.kt
// 职责：提取码输入弹窗（液态玻璃风格，替代 Material AlertDialog）
// 依赖：Compose ui.window.Dialog、GlassPanel、GlassButton、BasicTextField、Dimens、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.window.Dialog
import com.jieyundu.app.ui.glass.GlassButton
import com.jieyundu.app.ui.glass.GlassPanel
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 提取码输入弹窗（阶段 8 整改二）。
 *
 * 说明（D3）：不使用 Material `AlertDialog`，改用 Compose 原生 [Dialog] + 液态玻璃
 * [GlassPanel] 自绘；输入框为 [BasicTextField]，进入弹窗即自动聚焦。
 *
 * @param title 弹窗标题（来自 strings.xml）。
 * @param hint 输入框占位提示（来自 strings.xml）。
 * @param confirmText 确定按钮文案（来自 strings.xml）。
 * @param cancelText 取消按钮文案（来自 strings.xml）。
 * @param errorText 错误提示（如「提取码错误，请重试」）；无错误时为 null。
 * @param onConfirm 点击确定的回调，参数为用户输入的提取码。
 * @param onDismiss 关闭弹窗的回调（取消）。
 */
@Composable
fun PasswordDialog(
    title: String,
    hint: String,
    confirmText: String,
    cancelText: String,
    errorText: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = Dimens.CardCorner,
            contentPadding = Dimens.PanelPadding
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = JieYunDuColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(Dimens.SpaceLg))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.InputHeightCompact)
                        .clip(RoundedCornerShape(Dimens.InputCornerCompact))
                        .background(JieYunDuColors.InputFieldFill)
                        .padding(horizontal = Dimens.SpaceLg),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (input.isEmpty()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = JieYunDuColors.TextTertiary
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { value -> input = value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = JieYunDuColors.TextPrimary
                        ),
                        cursorBrush = SolidColor(JieYunDuColors.TextPrimary),
                        singleLine = true
                    )
                }
                if (errorText != null) {
                    Spacer(modifier = Modifier.height(Dimens.SpaceSm))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.labelMedium,
                        color = JieYunDuColors.TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(Dimens.SpaceXl))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassButton(
                        text = cancelText,
                        onClick = onDismiss,
                        height = Dimens.ButtonHeightCompact,
                        cornerRadius = Dimens.ButtonCornerCompact
                    )
                    Spacer(modifier = Modifier.width(Dimens.SpaceMd))
                    GlassButton(
                        text = confirmText,
                        onClick = { onConfirm(input) },
                        height = Dimens.ButtonHeightCompact,
                        cornerRadius = Dimens.ButtonCornerCompact,
                        fillColor = JieYunDuColors.ButtonFill,
                        borderColor = JieYunDuColors.ButtonBorder,
                        contentColor = JieYunDuColors.OnPrimary
                    )
                }
            }
        }
    }
}
