// 文件：DownloadScreen.kt
// 职责：下载页——任务列表（玻璃卡片）与空态提示；同时被首页平板右栏复用
// 依赖：DownloadViewModel、DownloadItem、WindowSizeHelper、Dimens、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.download

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyStateGlyph(modifier = Modifier.size(Dimens.EmptyIconSize))
                Spacer(modifier = Modifier.height(Dimens.SpaceLg))
                Text(
                    text = stringResource(R.string.download_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JieYunDuColors.TextSecondary
                )
            }
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

/**
 * 空态灰色下载图标（阶段 8 整改：空态需「居中图标 + 文案」）。
 *
 * 说明（D8）：不引入 material-icons-extended，以 Compose 原生 [Canvas] 自绘，
 * 与解析结果卡片的下载图标同构。
 *
 * @param modifier 外部修饰符。
 */
@Composable
private fun EmptyStateGlyph(modifier: Modifier = Modifier) {
    val color = JieYunDuColors.EmptyStateIcon
    Canvas(modifier = modifier) {
        val stroke = size.width * GLYPH_STROKE_RATIO
        val centerX = size.width / 2f
        val topY = size.height * GLYPH_TOP_RATIO
        val midY = size.height * GLYPH_MID_RATIO
        val baseY = size.height * GLYPH_BASE_RATIO
        val wing = size.width * GLYPH_WING_RATIO
        drawLine(
            color = color,
            start = Offset(centerX, topY),
            end = Offset(centerX, midY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(centerX - wing, midY - wing),
            end = Offset(centerX, midY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(centerX + wing, midY - wing),
            end = Offset(centerX, midY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * GLYPH_BASE_START_RATIO, baseY),
            end = Offset(size.width * GLYPH_BASE_END_RATIO, baseY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** 图标线宽相对边长比例。 */
private const val GLYPH_STROKE_RATIO = 0.06f

/** 箭头竖线起点占比。 */
private const val GLYPH_TOP_RATIO = 0.18f

/** 箭头交汇点占比。 */
private const val GLYPH_MID_RATIO = 0.62f

/** 底线纵向占比。 */
private const val GLYPH_BASE_RATIO = 0.82f

/** 箭头两翼长度占比。 */
private const val GLYPH_WING_RATIO = 0.18f

/** 底线起点横向占比。 */
private const val GLYPH_BASE_START_RATIO = 0.22f

/** 底线终点横向占比。 */
private const val GLYPH_BASE_END_RATIO = 0.78f