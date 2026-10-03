// 文件：SettingsScreen.kt
// 职责：设置页——已支持网盘、默认并发分片数、临时文件清理、崩溃日志导出、关于信息
// 依赖：SettingsViewModel、GlassCard、GlassButton、FileProvider、WindowSizeHelper、Dimens、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.jieyundu.app.R
import com.jieyundu.app.ui.adaptive.rememberIsExpandedLayout
import com.jieyundu.app.ui.glass.GlassButton
import com.jieyundu.app.ui.glass.GlassCard
import com.jieyundu.app.ui.screens.home.uiLabelRes
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors
import java.io.File

/**
 * 设置页。
 *
 * 说明：玻璃卡片分别展示支持范围、默认并发、临时文件清理、崩溃日志与关于信息；布局档位由
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
    val cleanupState by viewModel.cleanupState.collectAsState()
    val crashState by viewModel.crashState.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
    val context = LocalContext.current
    val chunkCount by viewModel.chunkCount.collectAsState()
    val directoryState by viewModel.directoryState.collectAsState()
    // B2 功能①：目录选择器返回 tree Uri → 解析为真实路径落库；A1 权限仅在用户「更改目录」时按需申请。
    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri -> viewModel.applyCustomDirectory(uri) }
    }
    val allFilesAccess = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 不强制校验授权结果：未授权时下载会回退私有目录并记日志，不中断用户操作。
        treePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        treePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }
    val requestCustomDirectory: () -> Unit = {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) {
                    treePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                } else {
                    val manageIntent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                    ).apply { data = Uri.parse("package:${context.packageName}") }
                    allFilesAccess.launch(manageIntent)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                treePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED ->
                treePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
            else -> storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    LaunchedEffect(directoryState.applyFailed) {
        if (directoryState.applyFailed) {
            Toast.makeText(context, R.string.settings_download_dir_failed, Toast.LENGTH_SHORT)
                .show()
            viewModel.consumeDirectoryApplyFailure()
        }
    }

    // 崩溃日志操作反馈：有可分享文件 → 系统分享；无日志 / 失败 / 已清空 → Toast 提示。
    // 依赖 crashState 整体（data class）作为 key，任一字段变化都会重新评估（修复追加）。
    LaunchedEffect(crashState) {
        val shareFile = crashState.shareFile
        when {
            shareFile != null -> {
                shareLogFile(context, shareFile, R.string.settings_crash_share_title)
                viewModel.consumeCrashShareFile()
            }

            crashState.noLogs -> {
                Toast.makeText(context, R.string.settings_crash_empty, Toast.LENGTH_SHORT).show()
            }

            crashState.failed -> {
                Toast.makeText(context, R.string.settings_crash_export_failed, Toast.LENGTH_SHORT)
                    .show()
            }

            crashState.clearedCount > 0 -> {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.settings_crash_cleared,
                        crashState.clearedCount
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 运行日志导出反馈：产出可分享文件 → 系统分享；失败 → Toast（B1）。
    LaunchedEffect(runtimeState) {
        val shareFile = runtimeState.shareFile
        when {
            shareFile != null -> {
                shareLogFile(context, shareFile, R.string.settings_runtime_share_title)
                viewModel.consumeRuntimeShareFile()
            }

            runtimeState.failed -> {
                Toast.makeText(
                    context,
                    R.string.settings_runtime_export_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

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
                    R.string.settings_chunk_hint,
                    viewModel.minChunkCount,
                    viewModel.maxChunkCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = JieYunDuColors.TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
            ) {
                viewModel.chunkCountOptions.forEach { option ->
                    ChunkOptionChip(
                        count = option,
                        selected = option == chunkCount,
                        onClick = { viewModel.setChunkCount(option) }
                    )
                }
            }
        }
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

/**
 * 并发档位胶囊（B2 功能②：32 / 64 / 128 / 256 / 512）。
 *
 * 说明：视觉与下载页筛选胶囊一致（圆角 = [Dimens.FilterChipCorner]，选中主色底 + 白字）。
 *
 * @param count 档位数值。
 * @param selected 是否选中。
 * @param onClick 点击回调。
 */
@Composable
private fun ChunkOptionChip(
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.FilterChipCorner)
    val background = if (selected) JieYunDuColors.Primary else JieYunDuColors.InputFieldFill
    val contentColor = if (selected) JieYunDuColors.OnPrimary else JieYunDuColors.TextSecondary
    Box(
        modifier = Modifier
            .height(Dimens.FilterChipHeight)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.SpaceMd),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

/**
 * 通过系统分享面板导出一个日志文件（崩溃日志 / 运行日志共用）。
 *
 * 说明：用 [FileProvider] 暴露 cache 下的日志目录，赋予临时读权限后走 [Intent.ACTION_SEND]；
 * 文件名由日志层生成（`jieyundu_crash_*.txt` / `jieyundu_runlog_*.txt`）。
 *
 * @param context 上下文（用于解析 FileProvider authority 与发起分享）。
 * @param file 待分享的日志文件。
 * @param titleRes 分享面板标题文案资源。
 */
private fun shareLogFile(context: Context, file: File, @StringRes titleRes: Int) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = LOG_MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, context.getString(titleRes))
    context.startActivity(chooser)
}

/** 日志分享 MIME 类型（纯文本）。 */
private const val LOG_MIME_TYPE = "text/plain"