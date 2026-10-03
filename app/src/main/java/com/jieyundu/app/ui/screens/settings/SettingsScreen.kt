// 文件：SettingsScreen.kt
// 职责：设置页——已支持网盘、默认并发分片数、关于信息
// 依赖：SettingsViewModel、GlassCard、WindowSizeHelper、Dimens、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jieyundu.app.R
import com.jieyundu.app.ui.adaptive.rememberIsExpandedLayout
import com.jieyundu.app.ui.glass.GlassCard
import com.jieyundu.app.ui.screens.home.uiLabelRes
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 设置页。
 *
 * 说明：三张玻璃卡片分别展示支持范围、默认并发与关于信息；布局档位由
 * [rememberIsExpandedLayout] 决定（9.4 / 9.5）。
 *
 * @param modifier 外部修饰符。
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val isExpanded = rememberIsExpandedLayout()
    val spacing = if (isExpanded) Dimens.SpaceXl else Dimens.SpaceMd
    val contentPadding: Dp =
        if (isExpanded) Dimens.PanelPadding else Dimens.PanelPaddingCompact

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        SettingsCard(
            title = stringResource(R.string.settings_supported_netdisks),
            contentPadding = contentPadding
        ) {
            if (viewModel.supportedTypes.isEmpty()) {
                Text(
                    text = stringResource(R.string.netdisk_quark),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JieYunDuColors.TextSecondary
                )
            } else {
                viewModel.supportedTypes.forEach { type ->
                    Text(
                        text = stringResource(type.uiLabelRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = JieYunDuColors.TextPrimary
                    )
                }
            }
        }
        SettingsCard(
            title = stringResource(R.string.settings_default_chunk),
            contentPadding = contentPadding
        ) {
            Text(
                text = stringResource(
                    R.string.settings_default_chunk_value,
                    viewModel.defaultChunkCount,
                    viewModel.maxChunkCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = JieYunDuColors.TextPrimary
            )
        }
        SettingsCard(
            title = stringResource(R.string.settings_about),
            contentPadding = contentPadding
        ) {
            Text(
                text = stringResource(R.string.settings_version_format, viewModel.versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = JieYunDuColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.settings_license),
                style = MaterialTheme.typography.bodyMedium,
                color = JieYunDuColors.TextSecondary
            )
        }
    }
}

/**
 * 设置页信息卡：标题 + 若干正文行。
 *
 * @param title 卡片标题。
 * @param contentPadding 卡内边距。
 * @param content 正文内容。
 */
@Composable
private fun SettingsCard(
    title: String,
    contentPadding: Dp,
    content: @Composable () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Dimens.CardCorner,
        contentPadding = contentPadding
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = JieYunDuColors.TextPrimary
            )
            content()
        }
    }
}
