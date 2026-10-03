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
import androidx.hilt.navigation.compose.hiltViewModel
import com.jieyundu.app.ui.adaptive.rememberIsExpandedLayout
import com.jieyundu.app.ui.screens.download.DownloadScreen
import com.jieyundu.app.ui.screens.home.components.LinkInputCard
import com.jieyundu.app.ui.screens.home.components.ParseResultCard
import com.jieyundu.app.ui.theme.Dimens

/** 平板左栏宽度占比（9.4：40%）。 */
private const val START_COLUMN_WEIGHT = 0.4f

/** 平板右栏宽度占比（9.4：60%）。 */
private const val END_COLUMN_WEIGHT = 0.6f

/**
 * 首页。
 *
 * 说明（9.4 / 9.5）：
 * - 平板（expanded）：左右两栏，左栏为输入区 + 解析结果预览，右栏复用 [DownloadScreen]（下载列表）；
 * - 手机（compact）：单栏纵向滚动，仅展示输入区与解析结果（下载内容在「下载」页签）。
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
                .padding(Dimens.SpaceXl),
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
}
