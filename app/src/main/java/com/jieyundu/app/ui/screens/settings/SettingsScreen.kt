// 文件：SettingsScreen.kt
// 职责：设置页**编排**——状态收集、权限与目录选择、操作反馈；各卡片见 settings/components/
// 依赖：SettingsViewModel、settings/components/*（10 张卡）、WindowSizeHelper、FileProvider、Dimens
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jieyundu.app.R
import com.jieyundu.app.ui.adaptive.rememberIsExpandedLayout
import com.jieyundu.app.ui.screens.settings.components.AboutCard
import com.jieyundu.app.ui.screens.settings.components.BackgroundCard
import com.jieyundu.app.ui.screens.settings.components.ChunkCountCard
import com.jieyundu.app.ui.screens.settings.components.ConcurrentTaskCard
import com.jieyundu.app.ui.screens.settings.components.DirectoryCard
import com.jieyundu.app.ui.screens.settings.components.LogExportCard
import com.jieyundu.app.ui.screens.settings.components.NetdiskSupportCard
import com.jieyundu.app.ui.screens.settings.components.RetryCountCard
import com.jieyundu.app.ui.screens.settings.components.SpeedLimitCard
import com.jieyundu.app.ui.screens.settings.components.TempCleanupCard
import com.jieyundu.app.ui.theme.Dimens
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
    // C2 第 1 条：从系统「电池优化」页返回后，重新读取真实白名单状态
    // （【修订 JYD-DEBT6-2026-10-07】接通原本写好了却没人调用的 refreshBatteryOptimizationStatus()）。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBatteryOptimizationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val isExpanded = rememberIsExpandedLayout()
    val spacing = if (isExpanded) Dimens.SpaceXl else Dimens.SpaceMd
    val contentPadding: Dp =
        if (isExpanded) Dimens.PanelPadding else Dimens.PanelPaddingCompact
    val cleanupState by viewModel.cleanupState.collectAsState()
    val crashState by viewModel.crashState.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
    val context = LocalContext.current
    val chunkCount by viewModel.chunkCount.collectAsState()
    val maxConcurrentTasks by viewModel.maxConcurrentTasks.collectAsState()
    val maxTaskRetries by viewModel.maxTaskRetries.collectAsState()
    val speedLimitBytesPerSecond by viewModel.speedLimitBytesPerSecond.collectAsState()
    val directoryState by viewModel.directoryState.collectAsState()
    // C2：后台保活与通知
    val keepDownloadingOnLock by viewModel.keepDownloadingOnLock.collectAsState()
    val downloadNotificationEnabled by viewModel.downloadNotificationEnabled.collectAsState()
    val batteryOptimizationExempt by viewModel.batteryOptimizationExempt.collectAsState()
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
        NetdiskSupportCard(viewModel = viewModel, contentPadding = contentPadding)
        ChunkCountCard(viewModel = viewModel, contentPadding = contentPadding)
        ConcurrentTaskCard(viewModel = viewModel, contentPadding = contentPadding)
        SpeedLimitCard(viewModel = viewModel, contentPadding = contentPadding)
        RetryCountCard(viewModel = viewModel, contentPadding = contentPadding)
        DirectoryCard(
            viewModel = viewModel,
            onRequestDirectory = requestCustomDirectory,
            contentPadding = contentPadding
        )
        BackgroundCard(
            viewModel = viewModel,
            context = context,
            contentPadding = contentPadding
        )
        TempCleanupCard(viewModel = viewModel, contentPadding = contentPadding)
        LogExportCard(viewModel = viewModel, contentPadding = contentPadding)
        AboutCard(viewModel = viewModel, contentPadding = contentPadding)
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
