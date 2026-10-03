// 文件：DownloadFilterBar.kt
// 职责：下载页顶部筛选条（全部 / 下载中 / 已完成）
// 依赖：JieYunDuColors、Dimens、DownloadFilter、Compose foundation
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.download.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.jieyundu.app.ui.screens.download.DownloadFilter
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 下载页筛选条（布局修订）。
 *
 * 说明：一排圆角胶囊；选中态为主色底 + 白字，未选中为浅灰底 + 次级文字色（浅色 Area 风）。
 * 胶囊圆角取 [Dimens.FilterChipCorner]（≈ 高度 × 0.33，**非全圆胶囊**，符合 9.2）。
 *
 * @param selected 当前选中的筛选档。
 * @param onSelect 点击某档的回调。
 * @param modifier 外部修饰符。
 */
@Composable
fun DownloadFilterBar(
    selected: DownloadFilter,
    onSelect: (DownloadFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(Dimens.FilterBarHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
    ) {
        DownloadFilter.entries.forEach { entry ->
            FilterChip(
                labelRes = entry.labelRes,
                selected = entry == selected,
                onClick = { onSelect(entry) }
            )
        }
    }
}

/**
 * 单个筛选胶囊。
 *
 * @param labelRes 文案资源 id。
 * @param selected 是否选中。
 * @param onClick 点击回调。
 */
@Composable
private fun FilterChip(
    @StringRes labelRes: Int,
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
            .padding(horizontal = Dimens.SpaceLg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}