// 文件：NetdiskPickerScreen.kt
// 职责：网盘选择页——列出支持的网盘并提供「登录」入口
// 依赖：GlassCard、GlassButton、NetdiskType、uiLabelRes、WindowSizeHelper、Dimens
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jieyundu.app.R
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.ui.adaptive.rememberIsExpandedLayout
import com.jieyundu.app.ui.glass.GlassButton
import com.jieyundu.app.ui.glass.GlassCard
import com.jieyundu.app.ui.screens.home.uiLabelRes
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 网盘选择页（阶段 11 入口，本轮先搭 UI）。
 *
 * 说明：列出全部支持的网盘并提供「登录」按钮；真实 WebView 登录与 Cookie 捕获将在
 * 下一轮（阶段 11）接入，本页当前仅呈现入口与说明，[onLogin] 作为回调占位。
 *
 * @param modifier 外部修饰符。
 * @param onLogin 点击某网盘「登录」的回调（阶段 11 接入真实登录）。
 */
@Composable
fun NetdiskPickerScreen(
    modifier: Modifier = Modifier,
    onLogin: (NetdiskType) -> Unit = {}
) {
    val isExpanded = rememberIsExpandedLayout()
    val spacing = if (isExpanded) Dimens.SpaceXl else Dimens.SpaceMd

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        Text(
            text = stringResource(R.string.netdisk_picker_title),
            style = MaterialTheme.typography.headlineMedium,
            color = JieYunDuColors.TextPrimary
        )
        Text(
            text = stringResource(R.string.netdisk_picker_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = JieYunDuColors.TextSecondary
        )
        NetdiskType.entries.forEach { type ->
            NetdiskRow(type = type, onLogin = { onLogin(type) })
        }
    }
}

/**
 * 单个网盘行：左侧名称与说明，右侧「登录」按钮。
 *
 * @param type 网盘类型。
 * @param onLogin 点击登录的回调。
 */
@Composable
private fun NetdiskRow(type: NetdiskType, onLogin: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Dimens.CardCorner,
        contentPadding = Dimens.SpaceLg
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(type.uiLabelRes()),
                    style = MaterialTheme.typography.titleMedium,
                    color = JieYunDuColors.TextPrimary
                )
                Text(
                    text = stringResource(R.string.netdisk_login_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = JieYunDuColors.TextTertiary
                )
            }
            Spacer(modifier = Modifier.width(Dimens.SpaceLg))
            GlassButton(
                text = stringResource(R.string.netdisk_login_action),
                onClick = onLogin,
                height = Dimens.ButtonHeightCompact,
                cornerRadius = Dimens.ButtonCornerCompact,
                fillColor = JieYunDuColors.ButtonFill,
                borderColor = JieYunDuColors.ButtonBorder,
                contentColor = JieYunDuColors.OnPrimary
            )
        }
    }
}
