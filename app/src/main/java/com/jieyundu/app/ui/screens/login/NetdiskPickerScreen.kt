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
 * 网盘选择页（阶段 11）。
 *
 * 说明：列出全部支持的网盘并提供「登录」入口；点击后由 [onLogin] 交由上层打开内嵌
 * WebView 登录页。[loggedInTypes] 中已登录的网盘展示「已登录」并提供 [onLogout] 退出。
 *
 * @param modifier 外部修饰符。
 * @param loggedInTypes 已登录的网盘集合（用于展示登录态）。
 * @param onLogin 点击某网盘「登录」的回调。
 * @param onLogout 点击某网盘「退出登录」的回调。
 */
@Composable
fun NetdiskPickerScreen(
    modifier: Modifier = Modifier,
    loggedInTypes: Set<NetdiskType> = emptySet(),
    onLogin: (NetdiskType) -> Unit = {},
    onLogout: (NetdiskType) -> Unit = {}
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
            NetdiskRow(
                type = type,
                loggedIn = type in loggedInTypes,
                onLogin = { onLogin(type) },
                onLogout = { onLogout(type) }
            )
        }
    }
}

/**
 * 单个网盘行：左侧名称与登录态说明，右侧「登录」或「退出登录」按钮。
 *
 * @param type 网盘类型。
 * @param loggedIn 是否已登录。
 * @param onLogin 点击登录的回调。
 * @param onLogout 点击退出的回调。
 */
@Composable
private fun NetdiskRow(
    type: NetdiskType,
    loggedIn: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
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
                    text = stringResource(
                        if (loggedIn) R.string.netdisk_logged_in else R.string.netdisk_login_hint
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = JieYunDuColors.TextTertiary
                )
            }
            Spacer(modifier = Modifier.width(Dimens.SpaceLg))
            GlassButton(
                text = stringResource(
                    if (loggedIn) R.string.netdisk_logout_action else R.string.netdisk_login_action
                ),
                onClick = if (loggedIn) onLogout else onLogin,
                height = Dimens.ButtonHeightCompact,
                cornerRadius = Dimens.ButtonCornerCompact,
                fillColor = if (loggedIn) JieYunDuColors.GlassFillStrong else JieYunDuColors.ButtonFill,
                borderColor = if (loggedIn) {
                    JieYunDuColors.GlassBorder
                } else {
                    JieYunDuColors.ButtonBorder
                },
                contentColor = if (loggedIn) JieYunDuColors.TextPrimary else JieYunDuColors.OnPrimary
            )
        }
    }
}
