// 文件：DownloadScreen.kt
// 职责：下载页——任务列表（玻璃卡片）与空态提示；同时被首页平板右栏复用
// 依赖：DownloadViewModel、DownloadItem、WindowSizeHelper、Dimens、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.jieyundu.app.R
import com.jieyundu.app.ui.adaptive.rememberIsExpandedLayout
import com.jieyundu.app.ui.screens.download.components.DownloadItem
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 下载页。
 *
 * 说明：数据来自 [DownloadViewModel.items]（Room 进度 × 会话文件名）；无任务时展示空态。
 * 列表项点击切换暂停 / 继续。平板与手机使用不同的卡片间距（9.4 / 9.5）。
 *
 * @param modifier 外部修饰符。
 */
@Composable
fun DownloadScreen(modifier: Modifier = Modifier) {
    val viewModel: DownloadViewModel = hiltViewModel()
    val downloadItems by viewModel.items.collectAsState()
    val isExpanded = rememberIsExpandedLayout()
    val spacing = if (isExpanded) Dimens.SpaceXl else Dimens.SpaceMd

    if (downloadItems.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(Dimens.SpaceXl),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.download_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = JieYunDuColors.TextSecondary
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            itemsIndexed(
                items = downloadItems,
                key = { _, item -> item.progress.taskId }
            ) { index, item ->
                DownloadItem(
                    item = item,
                    onClick = { viewModel.toggleTask(item) },
                    isExpanded = isExpanded,
                    index = index
                )
            }
        }
    }
}
