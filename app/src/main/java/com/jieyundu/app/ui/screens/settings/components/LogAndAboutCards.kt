// 文件：LogAndAboutCards.kt
// 职责：日志导出设置卡（崩溃日志 + 运行日志）与关于卡
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
 * 崩溃与运行日志卡（导出 / 清空 / 分享）。
 *
 * @param viewModel 设置页 ViewModel。
 * @param contentPadding 卡内边距。
 */
@Composable
internal fun LogExportCard(viewModel: SettingsViewModel, contentPadding: Dp) {
    val crashState by viewModel.crashState.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
    SettingsCard(
        title = stringResource(R.string.settings_crash_title),
        contentPadding = contentPadding
    ) {
    Text(
        text = stringResource(R.string.settings_crash_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = JieYunDuColors.TextSecondary
    )
    GlassButton(
        text = if (crashState.busy) {
            stringResource(R.string.settings_crash_exporting)
        } else {
            stringResource(R.string.settings_crash_export)
        },
        onClick = viewModel::exportCrashLog,
        height = Dimens.ButtonHeightCompact,
        cornerRadius = Dimens.ButtonCornerCompact,
        fillColor = JieYunDuColors.ButtonFill,
        borderColor = JieYunDuColors.ButtonBorder,
        contentColor = JieYunDuColors.OnPrimary
    )
    GlassButton(
        text = stringResource(R.string.settings_crash_clear),
        onClick = viewModel::clearCrashLogs,
        height = Dimens.ButtonHeightCompact,
        cornerRadius = Dimens.ButtonCornerCompact,
        fillColor = JieYunDuColors.GlassFillStrong,
        borderColor = JieYunDuColors.GlassBorder,
        contentColor = JieYunDuColors.TextPrimary
    )
    Text(
        text = stringResource(R.string.settings_runtime_export_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = JieYunDuColors.TextSecondary
    )
    GlassButton(
        text = if (runtimeState.busy) {
            stringResource(R.string.settings_runtime_exporting)
        } else {
            stringResource(R.string.settings_runtime_export)
        },
        onClick = viewModel::exportRuntimeLog,
        height = Dimens.ButtonHeightCompact,
        cornerRadius = Dimens.ButtonCornerCompact,
        fillColor = JieYunDuColors.GlassFillStrong,
        borderColor = JieYunDuColors.GlassBorder,
        contentColor = JieYunDuColors.TextPrimary
    )
    }
}

/**
 * 关于卡（版本号 + 许可证）。
 *
 * @param viewModel 设置页 ViewModel。
 * @param contentPadding 卡内边距。
 */
@Composable
internal fun AboutCard(viewModel: SettingsViewModel, contentPadding: Dp) {
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
