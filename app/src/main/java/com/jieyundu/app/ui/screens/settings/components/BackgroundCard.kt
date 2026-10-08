// 文件：BackgroundCard.kt
// 职责：后台保活与通知设置卡（C2）+ 电池优化白名单入口
// 依赖：SettingsCard、SettingToggleRow、GlassButton、SettingsViewModel、Dimens、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.settings.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.jieyundu.app.R
import com.jieyundu.app.ui.components.SettingToggleRow
import com.jieyundu.app.ui.glass.GlassButton
import com.jieyundu.app.ui.screens.settings.SettingsViewModel
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors
import timber.log.Timber

/**
 * 后台与通知卡（C2 第 1、2 条 + 电池优化白名单入口）。
 *
 * @param viewModel 设置页 ViewModel。
 * @param context 上下文（打开系统电池优化页用）。
 * @param contentPadding 卡内边距。
 */
@Composable
internal fun BackgroundCard(
    viewModel: SettingsViewModel,
    context: Context,
    contentPadding: Dp
) {
    val keepDownloadingOnLock by viewModel.keepDownloadingOnLock.collectAsState()
    val downloadNotificationEnabled by viewModel.downloadNotificationEnabled.collectAsState()
    val batteryOptimizationExempt by viewModel.batteryOptimizationExempt.collectAsState()
    SettingsCard(
        title = stringResource(R.string.settings_background_title),
        contentPadding = contentPadding
    ) {
    SettingToggleRow(
        title = stringResource(R.string.settings_keep_downloading_title),
        description = stringResource(R.string.settings_keep_downloading_hint),
        checked = keepDownloadingOnLock,
        onCheckedChange = viewModel::setKeepDownloadingOnLock,
        contentPadding = Dimens.SpaceLg
    )
    SettingToggleRow(
        title = stringResource(R.string.settings_download_notification_title),
        description = stringResource(R.string.settings_download_notification_hint),
        checked = downloadNotificationEnabled,
        onCheckedChange = viewModel::setDownloadNotificationEnabled,
        contentPadding = Dimens.SpaceLg
    )
    if (!batteryOptimizationExempt) {
        Text(
            text = stringResource(R.string.settings_battery_optimization_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = JieYunDuColors.TextSecondary
        )
        GlassButton(
            text = stringResource(R.string.settings_battery_optimization_action),
            onClick = { openBatteryOptimizationSettings(context) },
            height = Dimens.ButtonHeightCompact,
            cornerRadius = Dimens.ButtonCornerCompact,
            fillColor = JieYunDuColors.GlassFillStrong,
            borderColor = JieYunDuColors.GlassBorder,
            contentColor = JieYunDuColors.TextPrimary
        )
    } else {
        Text(
            text = stringResource(R.string.settings_battery_optimization_done),
            style = MaterialTheme.typography.bodyMedium,
            color = JieYunDuColors.TextSecondary
        )
    }
    }
}

/**
 * 打开「忽略电池优化」系统设置页（C2 第 1 条）。
 *
 * 说明：优先直达本 App 的电池优化授权弹窗（[Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS]，
 * 需 manifest 声明 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限）；厂商 ROM 上该 Intent 可能不存在，
 * 此时回退到电池优化设置列表页（[Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS]）。
 * 两者都不可用时只记日志，不抛异常、不打断用户操作（D15）。
 *
 * @param context 上下文。
 */
internal fun openBatteryOptimizationSettings(context: Context) {
    val directIntent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    ).apply { data = Uri.parse("package:${context.packageName}") }
    try {
        context.startActivity(directIntent)
        return
    } catch (exception: ActivityNotFoundException) {
        Timber.w(exception, "SettingsScreen: no direct battery optimization activity, fallback")
    } catch (exception: SecurityException) {
        Timber.w(exception, "SettingsScreen: battery optimization direct intent denied, fallback")
    }
    try {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (exception: ActivityNotFoundException) {
        Timber.w(exception, "SettingsScreen: no battery optimization settings activity")
    }
}