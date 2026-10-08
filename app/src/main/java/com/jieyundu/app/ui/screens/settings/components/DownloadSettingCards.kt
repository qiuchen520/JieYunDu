// 文件：DownloadSettingCards.kt
// 职责：「下载与解析能力」设置卡：支持范围 + 分片数 / 同时任务数 / 限速 / 重试次数
// 依赖：SettingsCard/SettingsOptionRow、SettingsViewModel、Dimens、strings.xml
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
import com.jieyundu.app.ui.screens.home.uiLabelRes
import com.jieyundu.app.ui.screens.settings.SettingsViewModel
import com.jieyundu.app.ui.theme.JieYunDuColors

/** 每 MB 字节数（C1：限速档位展示换算用）。 */
private const val BYTES_PER_MB = 1_048_576L

/**
 * 已支持网盘卡。
 *
 * @param viewModel 设置页 ViewModel（提供已注册网盘类型）。
 * @param contentPadding 卡内边距。
 */
@Composable
internal fun NetdiskSupportCard(viewModel: SettingsViewModel, contentPadding: Dp) {
    SettingsCard(
        title = stringResource(R.string.settings_supported_netdisks),
        contentPadding = contentPadding
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
    }
}

/**
 * 默认分片数卡。
 *
 * @param viewModel 设置页 ViewModel。
 * @param contentPadding 卡内边距。
 */
@Composable
internal fun ChunkCountCard(viewModel: SettingsViewModel, contentPadding: Dp) {
    val selected by viewModel.chunkCount.collectAsState()
    SettingsOptionRow(
        titleRes = R.string.settings_default_chunk,
        hint = stringResource(
            R.string.settings_chunk_hint,
            viewModel.minChunkCount,
            viewModel.maxChunkCount
        ),
        options = viewModel.chunkCountOptions,
        selected = selected,
        labelFor = { option -> option.toString() },
        onSelect = viewModel::setChunkCount,
        contentPadding = contentPadding
    )
}

/**
 * 同时下载任务数卡。
 *
 * @param viewModel 设置页 ViewModel。
 * @param contentPadding 卡内边距。
 */
@Composable
internal fun ConcurrentTaskCard(viewModel: SettingsViewModel, contentPadding: Dp) {
    val selected by viewModel.maxConcurrentTasks.collectAsState()
    SettingsOptionRow(
        titleRes = R.string.settings_download_concurrent_title,
        hint = stringResource(R.string.settings_download_concurrent_hint),
        options = viewModel.maxConcurrentTaskOptions,
        selected = selected,
        labelFor = { option -> option.toString() },
        onSelect = viewModel::setMaxConcurrentTasks,
        contentPadding = contentPadding
    )
}

/**
 * 下载限速卡（0 表示不限速）。
 *
 * @param viewModel 设置页 ViewModel。
 * @param contentPadding 卡内边距。
 */
@Composable
internal fun SpeedLimitCard(viewModel: SettingsViewModel, contentPadding: Dp) {
    val selected by viewModel.speedLimitBytesPerSecond.collectAsState()
    SettingsOptionRow(
        titleRes = R.string.settings_download_speed_title,
        hint = stringResource(R.string.settings_download_speed_hint),
        options = viewModel.speedLimitOptionsBytesPerSecond,
        selected = selected,
        labelFor = { option ->
            if (option <= 0L) {
                stringResource(R.string.settings_speed_unlimited)
            } else {
                stringResource(R.string.settings_speed_option_mbps, (option / BYTES_PER_MB).toInt())
            }
        },
        onSelect = viewModel::setSpeedLimitBytesPerSecond,
        contentPadding = contentPadding
    )
}

/**
 * 任务失败重试次数卡（0 表示不重试）。
 *
 * @param viewModel 设置页 ViewModel。
 * @param contentPadding 卡内边距。
 */
@Composable
internal fun RetryCountCard(viewModel: SettingsViewModel, contentPadding: Dp) {
    val selected by viewModel.maxTaskRetries.collectAsState()
    SettingsOptionRow(
        titleRes = R.string.settings_download_retry_title,
        hint = stringResource(R.string.settings_download_retry_hint),
        options = viewModel.maxTaskRetryOptions,
        selected = selected,
        labelFor = { option ->
            if (option <= 0) {
                stringResource(R.string.settings_retry_off)
            } else {
                stringResource(R.string.settings_retry_option_times, option)
            }
        },
        onSelect = viewModel::setMaxTaskRetries,
        contentPadding = contentPadding
    )
}
