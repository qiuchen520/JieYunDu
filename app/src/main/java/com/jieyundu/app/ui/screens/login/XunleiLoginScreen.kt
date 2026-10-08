// 文件：XunleiLoginScreen.kt
// 职责：迅雷原生登录页（账号密码 / 短信两种模式；风控提示）——不走网页登录
// 依赖：XunleiLoginViewModel、GlassPanel/GlassCard/GlassButton/GlassChip、BasicTextField、Dimens、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import com.jieyundu.app.R
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.ui.components.GlassChip
import com.jieyundu.app.ui.glass.GlassButton
import com.jieyundu.app.ui.glass.GlassCard
import com.jieyundu.app.ui.screens.home.uiLabelRes
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 迅雷原生登录页（【JYD-XUNLEI-P1B-2026-10-08】）。
 *
 * 为什么不是 WebView：迅雷的登录态是 OAuth2 双令牌，由**App 端凭据的原生接口**下发，
 * `pan.xunlei.com` 网页本身不发 `access_token`（《抓包事实.md》§4「登录链路」）。
 * 内嵌 WebView 只在**风控安全验证**时使用（下一批落地）。
 *
 * 视觉沿用既有玻璃体系（不改配色 / 圆角 / 质感），输入框用自绘 [BasicTextField]（R5/D3）。
 *
 * @param modifier 外部修饰符。
 * @param onClose 关闭登录页回调。
 * @param onLoggedIn 登录成功回调（页面据此返回网盘列表）。
 * @param viewModel 登录页状态机。
 */
@Composable
fun XunleiLoginScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onLoggedIn: () -> Unit,
    viewModel: XunleiLoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val reviewUrl = state.reviewUrl

    if (reviewUrl != null) {
        // 风控安全验证（§4 ⑤）：内嵌页面完成验证后自动重试登录。
        XunleiReviewWebView(
            reviewUrl = reviewUrl,
            deviceId = viewModel.deviceId,
            onVerified = viewModel::onReviewVerified,
            onClose = viewModel::dismissReview,
            modifier = modifier
                .fillMaxSize()
                .background(JieYunDuColors.Background)
                .padding(Dimens.SpaceLg)
        )
        return
    }

    // 登录成功：通知上层标记登录态并返回。
    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) {
            onLoggedIn()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JieYunDuColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(Dimens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = Dimens.CardCorner,
            contentPadding = Dimens.SpaceLg
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.webview_login_title,
                            stringResource(NetdiskType.XUNLEI.uiLabelRes())
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = JieYunDuColors.TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.xunlei_login_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = JieYunDuColors.TextTertiary
                    )
                }
                GlassButton(
                    text = stringResource(R.string.webview_login_close),
                    onClick = onClose,
                    height = Dimens.ButtonHeightCompact,
                    cornerRadius = Dimens.ButtonCornerCompact,
                    fillColor = JieYunDuColors.GlassFillStrong,
                    borderColor = JieYunDuColors.GlassBorder,
                    contentColor = JieYunDuColors.TextPrimary
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
        ) {
            GlassChip(
                label = stringResource(R.string.xunlei_login_mode_password),
                selected = state.mode == XunleiLoginViewModel.Mode.PASSWORD,
                onClick = { viewModel.switchMode(XunleiLoginViewModel.Mode.PASSWORD) }
            )
            GlassChip(
                label = stringResource(R.string.xunlei_login_mode_sms),
                selected = state.mode == XunleiLoginViewModel.Mode.SMS,
                onClick = { viewModel.switchMode(XunleiLoginViewModel.Mode.SMS) }
            )
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = Dimens.CardCorner,
            contentPadding = Dimens.SpaceLg
        ) {
            if (state.mode == XunleiLoginViewModel.Mode.PASSWORD) {
                XunleiInputField(
                    label = stringResource(R.string.xunlei_login_account),
                    hint = stringResource(R.string.xunlei_login_account_hint),
                    value = state.account,
                    onValueChange = viewModel::onAccountChange
                )
                XunleiInputField(
                    label = stringResource(R.string.xunlei_login_password),
                    hint = stringResource(R.string.xunlei_login_password),
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    maskInput = true
                )
            } else {
                XunleiInputField(
                    label = stringResource(R.string.xunlei_login_mobile),
                    hint = stringResource(R.string.xunlei_login_mobile),
                    value = state.mobile,
                    onValueChange = viewModel::onMobileChange
                )
                XunleiInputField(
                    label = stringResource(R.string.xunlei_login_sms_code),
                    hint = stringResource(R.string.xunlei_login_sms_code),
                    value = state.smsCode,
                    onValueChange = viewModel::onSmsCodeChange
                )
                GlassButton(
                    text = stringResource(R.string.xunlei_login_send_sms),
                    onClick = viewModel::sendSms,
                    enabled = state.canSendSms,
                    height = Dimens.ButtonHeightCompact,
                    cornerRadius = Dimens.ButtonCornerCompact,
                    fillColor = JieYunDuColors.GlassFillStrong,
                    borderColor = JieYunDuColors.GlassBorder,
                    contentColor = JieYunDuColors.TextPrimary
                )
            }

            GlassButton(
                text = stringResource(R.string.netdisk_login_action),
                onClick = {
                    if (state.mode == XunleiLoginViewModel.Mode.PASSWORD) {
                        viewModel.loginWithPassword()
                    } else {
                        viewModel.loginWithSms()
                    }
                },
                enabled = state.canSubmit && !state.busy,
                height = Dimens.ButtonHeight,
                cornerRadius = Dimens.ButtonCorner,
                fillColor = JieYunDuColors.Primary,
                borderColor = JieYunDuColors.ButtonBorder,
                contentColor = JieYunDuColors.OnPrimary
            )

            state.messageRes?.let { messageRes ->
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JieYunDuColors.TextSecondary
                )
            }
            state.serverMessage?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = JieYunDuColors.TextTertiary
                )
            }
        }
    }
}

/**
 * 自绘输入框（标签 + 玻璃底 [BasicTextField]）。
 *
 * 依据 D3/R5：不使用 Material 默认 `TextField` 样式，与首页链接框 / 提取码弹窗保持一致。
 *
 * @param label 字段名。
 * @param hint 占位提示。
 * @param value 当前值。
 * @param onValueChange 值变更回调。
 * @param maskInput 是否掩码显示（密码）。
 */
@Composable
private fun XunleiInputField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    maskInput: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = JieYunDuColors.TextSecondary
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.InputHeightCompact)
                .clip(RoundedCornerShape(Dimens.InputCornerCompact))
                .background(JieYunDuColors.InputFieldFill)
                .padding(horizontal = Dimens.SpaceLg),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = hint,
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
                singleLine = true,
                visualTransformation = if (maskInput) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                }
            )
        }
    }
}
