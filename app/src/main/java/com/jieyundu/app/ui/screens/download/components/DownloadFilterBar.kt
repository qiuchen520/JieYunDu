// 文件：DownloadFilterBar.kt
// 职责：下载页顶部筛选条（全部 / 下载中 / 已完成），整条为玻璃面板
// 依赖：GlassPanel、GlassChip、Dimens、DownloadFilter
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.download.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jieyundu.app.ui.components.GlassChip
import com.jieyundu.app.ui.glass.GlassPanel
import com.jieyundu.app.ui.screens.download.DownloadFilter
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 下载页筛选条（布局修订 + B2 整页玻璃化）。
 *
 * 说明：整条筛选条本身是一块**玻璃面板**（半透明白底 + 边缘高光 + 极淡内阴影），
 * 内部三枚档位胶囊同为玻璃质感（见 [GlassChip]）；选中态仍是主色底 + 白字，未选中为
 * 玻璃底 + 次级文字色——配色与圆角全部沿用既有常量（不改视觉基准）。
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
    GlassPanel(
        modifier = modifier,
        cornerRadius = Dimens.CardCorner,
        contentPadding = Dimens.SpaceSm,
        fillColor = JieYunDuColors.GlassFillStrong,
        borderColor = JieYunDuColors.GlassBorder
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
        ) {
            DownloadFilter.entries.forEach { entry ->
                GlassChip(
                    labelRes = entry.labelRes,
                    selected = entry == selected,
                    onClick = { onSelect(entry) }
                )
            }
        }
    }
}
