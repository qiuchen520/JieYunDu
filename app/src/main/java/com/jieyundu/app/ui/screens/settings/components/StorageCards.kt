// 文件：StorageCards.kt
// 职责：存储相关设置卡：下载目录 + 临时文件清理
// 依赖：SettingsCard、GlassButton、SettingsViewModel、Dimens、strings.xml
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.settings.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.jieyundu.app.R
import com.jieyundu.app.ui.glass.GlassButton
import com.jieyundu.app.ui.screens.settings.SettingsViewModel
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 下载目录卡（默认公共下载目录 / 自定义目录）。
 *
 * @param viewModel 设置页 ViewModel。
 * @param onRequestDirectory 申请「更改目录」（含权限流程，由设置页编排）。
 * @param contentPadding 卡内边距。
 */
@Composable
internal fun DirectoryCard(
    viewModel: SettingsViewModel,
    onRequestDirectory: () -> Unit,
    contentPadding: Dp
) {
    val directoryState by viewModel.directoryState.collectAsState()
    SettingsCard(
        title = stringResource(R.string.settings_download_dir),
        contentPadding = contentPadding
    ) {
    Text(
        text = if (directoryState.isCustom) {
            directoryState.customPath
                ?: stringResource(R.string.settings_download_dir_default_value)
        } else {
            stringResource(
                R.string.settings_download_dir_public_format,
                directoryState.publicFolderName
            )
        },
        style = MaterialTheme.typography.bodyMedium,
        color = JieYunDuColors.TextPrimary
    )
    Text(
        text = if (directoryState.isCustom) {
            stringResource(R.string.settings_download_dir_custom_hint)
        } else {
            stringResource(R.string.settings_download_dir_public_hint)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = JieYunDuColors.TextSecondary
    )
    GlassButton(
        text = stringResource(R.string.settings_download_dir_change),
        onClick = requestCustomDirectory,
        height = Dimens.ButtonHeightCompact,
        cornerRadius = Dimens.ButtonCornerCompact,
        fillColor = JieYunDuColors.ButtonFill,
        borderColor = JieYunDuColors.ButtonBorder,
        contentColor = JieYunDuColors.OnPrimary
    )
    if (directoryState.isCustom) {
        GlassButton(
            text = stringResource(R.string.settings_download_dir_reset),
            onClick = viewModel::resetDownloadDirectory,
            height = Dimens.ButtonHeightCompact,
            cornerRadius = Dimens.ButtonCornerCompact,
            fillColor = JieYunDuColors.GlassFillStrong,
            borderColor = JieYunDuColors.GlassBorder,
            contentColor = JieYunDuColors.TextPrimary
        )
    }
    }
}

/**
 * 临时文件清理卡（手动清理网盘临时目录中的转存副本）。
 *
 * @param viewModel 设置页 ViewModel。
 * @param contentPadding 卡内边距。
 */
@Composable
internal fun TempCleanupCard(viewModel: SettingsViewModel, contentPadding: Dp) {
    val cleanupState by viewModel.cleanupState.collectAsState()
    SettingsCard(
        title = stringResource(R.string.settings_temp_cleanup),
        contentPadding = contentPadding
    ) {
    Text(
        text = stringResource(R.string.settings_temp_cleanup_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = JieYunDuColors.TextSecondary
    )
    GlassButton(
        text = stringResource(R.string.settings_temp_cleanup_action),
        onClick = viewModel::cleanupTempFiles,
        height = Dimens.ButtonHeightCompact,
        cornerRadius = Dimens.ButtonCornerCompact,
        fillColor = JieYunDuColors.ButtonFill,
        borderColor = JieYunDuColors.ButtonBorder,
        contentColor = JieYunDuColors.OnPrimary
    )
    val statusText = when {
        cleanupState.running -> stringResource(R.string.settings_temp_cleanup_running)
        cleanupState.completed -> stringResource(
            R.string.settings_temp_cleanup_done,
            cleanupState.deletedCount
        )
        cleanupState.failed -> stringResource(R.string.settings_temp_cleanup_failed)
        else -> null
    }
    if (statusText != null) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = JieYunDuColors.TextPrimary
        )
    }
    }
}
