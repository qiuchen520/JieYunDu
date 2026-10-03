// 文件：HomeScreen.kt
// 职责：首页——链接输入、解析结果展示；平板档位右侧内嵌下载列表（9.4 两栏）
// 依赖：HomeViewModel、LinkInputCard、ParseResultCard、DownloadScreen、WindowSizeHelper
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.jieyundu.app.R
import com.jieyundu.app.ui.adaptive.rememberIsExpandedLayout
import com.jieyundu.app.ui.screens.download.DownloadScreen
import com.jieyundu.app.ui.screens.home.components.LinkInputCard
import com.jieyundu.app.ui.screens.home.components.ParseResultCard
import com.jieyundu.app.ui.screens.home.components.PasswordDialog
import com.jieyundu.app.ui.theme.Dimens

/** 平板左栏宽度占比（阶段 8 整改：输入区约 60%）。 */
private const val START_COLUMN_WEIGHT = 0.6f

/** 平板右栏宽度占比（阶段 8 整改：空态区约 40%）。 */
private const val END_COLUMN_WEIGHT = 0.4f

/**
 * 首页。
 *
 * 说明（9.4 / 9.5 + 阶段 8 整改）：
 * - 平板（expanded）：左右两栏，左栏约 60% 为输入区 + 解析结果，右栏约 40% 复用
 *   [DownloadScreen]（无任务时居中显示空态）；
 * - 手机（compact）：单栏纵向滚动，仅展示输入区与解析结果；
 * - 当服务器要求提取码时（[HomeUiState.passwordPrompt]），弹出液态玻璃 [PasswordDialog]，
 *   不中断流程、不引导用户改链接。
 *
 * @param modifier 外部修饰符。
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val isExpanded = rememberIsExpandedLayout()

    if (isExpanded) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.SpaceXl)
                .padding(top = Dimens.ContentTopPadding, bottom = Dimens.SpaceXl),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXl)
        ) {
            Column(
                modifier = Modifier
                    .weight(START_COLUMN_WEIGHT)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXl)
            ) {
                LinkInputCard(
                    input = state.inputLink,
                    onInputChange = viewModel::onInputChange,
                    onParse = viewModel::parse,
                    isParsing = state.isParsing,
                    isExpanded = true
                )
                ParseResultCard(
                    result = state.result,
                    errorRes = state.errorRes,
                    isParsing = state.isParsing,
                    onDownload = viewModel::download,
                    isExpanded = true
                )
            }
            DownloadScreen(
                modifier = Modifier
                    .weight(END_COLUMN_WEIGHT)
                    .fillMaxHeight()
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
        ) {
            LinkInputCard(
                input = state.inputLink,
                onInputChange = viewModel::onInputChange,
                onParse = viewModel::parse,
                isParsing = state.isParsing,
                isExpanded = false
            )
            ParseResultCard(
                result = state.result,
                errorRes = state.errorRes,
                isParsing = state.isParsing,
                onDownload = viewModel::download,
                isExpanded = false
            )
        }
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