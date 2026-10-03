// 文件：HomeScreen.kt
// 职责：首页——链接输入（含选填提取码）、解析、结果选择与下载
// 依赖：HomeViewModel、LinkInputCard、ParseResultCard、PasswordDialog、WindowSizeHelper、Dimens
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.jieyundu.app.R
import com.jieyundu.app.ui.adaptive.rememberIsExpandedLayout
import com.jieyundu.app.ui.screens.home.components.LinkInputCard
import com.jieyundu.app.ui.screens.home.components.ParseResultCard
import com.jieyundu.app.ui.screens.home.components.PasswordDialog
import com.jieyundu.app.ui.theme.Dimens

/**
 * 首页（布局修订：单栏「输入 → 解析 → 选文件 → 下载」）。
 *
 * 说明：
 * - 顶部输入卡：链接输入框 + 选填提取码 + 解析按钮；
 * - 下方结果卡占据剩余空间，用于展示解析出的文件列表并勾选下载；
 * - 任务列表由「下载」页呈现（布局修订：首页不再内嵌下载列表）；
 * - 需要提取码时弹出液态玻璃 [PasswordDialog]（不中断流程）。
 *
 * @param modifier 外部修饰符。
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val isExpanded = rememberIsExpandedLayout()
    val spacing = if (isExpanded) Dimens.SpaceXl else Dimens.SpaceMd
    val breadcrumb = state.stack.joinToString(
        separator = stringResource(R.string.parse_breadcrumb_separator)
    ) { level -> level.name }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        LinkInputCard(
            input = state.inputLink,
            onInputChange = viewModel::onInputChange,
            code = state.inputCode,
            onCodeChange = viewModel::onCodeChange,
            onParse = viewModel::parse,
            isParsing = state.isParsing,
            isExpanded = isExpanded
        )
        ParseResultCard(
            result = state.result,
            errorRes = state.errorRes,
            isParsing = state.isParsing,
            level = state.currentLevel,
            breadcrumb = breadcrumb,
            canNavigateUp = state.canNavigateUp,
            isLoadingDir = state.isLoadingDir,
            dirErrorRes = state.dirErrorRes,
            isPreparingDownload = state.isPreparingDownload,
            downloadErrorRes = state.downloadErrorRes,
            onDownload = viewModel::download,
            onOpenFolder = viewModel::openFolder,
            onNavigateUp = viewModel::navigateUp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            isExpanded = isExpanded
        )
    }

    val passwordErrorRes = state.passwordErrorRes
    if (state.passwordPrompt) {
        PasswordDialog(
            title = stringResource(R.string.password_dialog_title),
            hint = stringResource(R.string.password_dialog_hint),
            confirmText = stringResource(R.string.action_confirm),
            cancelText = stringResource(R.string.action_cancel),
            errorText = if (passwordErrorRes != null) stringResource(passwordErrorRes) else null,
            onConfirm = viewModel::submitPassword,
            onDismiss = viewModel::dismissPasswordPrompt
        )
    }
}