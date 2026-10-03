// 文件：NetdiskBrowserScreen.kt
// 职责：网盘管理页——展示个人网盘容量、目录列表，支持进入文件夹与返回上一级（流程 B）
// 依赖：NetdiskBrowserState、GlassCard、GlassButton、FileSizeFormatter、uiLabelRes、Dimens、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.jieyundu.app.R
import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.QuotaInfo
import com.jieyundu.app.domain.util.FileSizeFormatter
import com.jieyundu.app.ui.adaptive.rememberIsExpandedLayout
import com.jieyundu.app.ui.glass.GlassButton
import com.jieyundu.app.ui.glass.GlassCard
import com.jieyundu.app.ui.screens.home.uiLabelRes
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 网盘管理页（流程 B，B1：夸克）。
 *
 * 结构：标题 + 「返回网盘列表」；容量卡；面包屑 + 「返回上一级」；目录列表（可滚动）。
 *
 * @param state 管理页状态。
 * @param onOpenFolder 点击文件夹进入下一级。
 * @param onNavigateUp 点击「返回上一级」。
 * @param onClose 关闭管理页回到网盘列表。
 * @param modifier 外部修饰符。
 */
@Composable
fun NetdiskBrowserScreen(
    state: NetdiskBrowserState,
    onOpenFolder: (FileInfo) -> Unit,
    onNavigateUp: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpanded = rememberIsExpandedLayout()
    val spacing = if (isExpanded) Dimens.SpaceXl else Dimens.SpaceMd
    val contentPadding: Dp =
        if (isExpanded) Dimens.PanelPadding else Dimens.PanelPaddingCompact
    val typeLabel = state.netdiskType?.let { type -> stringResource(type.uiLabelRes()) }.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.netdisk_browser_title),
                style = MaterialTheme.typography.headlineMedium,
                color = JieYunDuColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            GlassButton(
                text = stringResource(R.string.netdisk_browser_back),
                onClick = onClose,
                height = Dimens.ButtonHeightCompact,
                cornerRadius = Dimens.ButtonCornerCompact,
                fillColor = JieYunDuColors.GlassFillStrong,
                borderColor = JieYunDuColors.GlassBorder,
                contentColor = JieYunDuColors.TextPrimary
            )
        }

        if (state.unsupported) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Dimens.CardCorner,
                contentPadding = contentPadding
            ) {
                Text(
                    text = stringResource(R.string.netdisk_browser_unsupported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JieYunDuColors.TextSecondary
                )
            }
            return@Column
        }

        if (typeLabel.isNotBlank()) {
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.titleMedium,
                color = JieYunDuColors.TextPrimary
            )
        }

        QuotaCard(quota = state.quota, contentPadding = contentPadding)

        val level = state.currentLevel
        // 注意：joinToString 的 lambda 不是 @Composable 上下文，stringResource 必须在此提前求值。
        val rootLabel = stringResource(R.string.netdisk_browser_root)
        val breadcrumb = state.stack.joinToString(separator = BREADCRUMB_SEPARATOR) { item ->
            item.name.ifBlank { rootLabel }
        }
        if (breadcrumb.isNotBlank()) {
            Text(
                text = breadcrumb,
                style = MaterialTheme.typography.labelSmall,
                color = JieYunDuColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (state.canNavigateUp) {
            Text(
                text = stringResource(R.string.netdisk_browser_up),
                modifier = Modifier.clickable(onClick = onNavigateUp),
                style = MaterialTheme.typography.labelLarge,
                color = JieYunDuColors.Primary
            )
        }

        val files = level?.files.orEmpty()
        val errorRes = state.errorRes
        when {
            state.isLoading -> Text(
                text = stringResource(R.string.netdisk_browser_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = JieYunDuColors.TextSecondary
            )

            errorRes != null -> Text(
                text = stringResource(errorRes),
                style = MaterialTheme.typography.bodyMedium,
                color = JieYunDuColors.TextSecondary
            )

            files.isEmpty() -> Text(
                text = stringResource(R.string.netdisk_browser_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = JieYunDuColors.TextSecondary
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)
            ) {
                items(files) { file ->
                    BrowserEntryRow(file = file, onOpen = { onOpenFolder(file) })
                }
            }
        }
    }
}

/**
 * 容量卡：展示已用 / 总量 / 剩余。
 *
 * @param quota 容量信息；为 null 时展示「容量获取失败」。
 * @param contentPadding 卡内边距。
 */
@Composable
private fun QuotaCard(quota: QuotaInfo?, contentPadding: Dp) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Dimens.CardCorner,
        contentPadding = contentPadding
    ) {
        Text(
            text = stringResource(R.string.netdisk_browser_quota),
            style = MaterialTheme.typography.titleMedium,
            color = JieYunDuColors.TextPrimary
        )
        if (quota == null) {
            Text(
                text = stringResource(R.string.netdisk_browser_quota_unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = JieYunDuColors.TextSecondary
            )
        } else {
            Text(
                text = stringResource(
                    R.string.netdisk_browser_quota_format,
                    FileSizeFormatter.format(quota.used),
                    FileSizeFormatter.format(quota.total)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = JieYunDuColors.TextPrimary
            )
            Text(
                text = stringResource(
                    R.string.netdisk_browser_quota_remaining,
                    FileSizeFormatter.format(quota.remaining)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = JieYunDuColors.TextTertiary
            )
        }
    }
}

/**
 * 单条目录条目行：文件夹可点击进入，普通文件仅展示名称与大小。
 *
 * @param file 文件条目。
 * @param onOpen 点击回调（文件夹进入）。
 */
@Composable
private fun BrowserEntryRow(file: FileInfo, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = file.isDirectory, onClick = onOpen)
            .padding(vertical = Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.fileName,
                style = MaterialTheme.typography.titleMedium,
                color = JieYunDuColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (file.isDirectory) {
                    stringResource(R.string.netdisk_browser_item_folder)
                } else {
                    FileSizeFormatter.format(file.fileSize)
                },
                style = MaterialTheme.typography.labelSmall,
                color = JieYunDuColors.TextTertiary
            )
        }
        if (file.isDirectory) {
            Spacer(modifier = Modifier.width(Dimens.SpaceSm))
            Text(
                text = FOLDER_INDICATOR,
                style = MaterialTheme.typography.titleMedium,
                color = JieYunDuColors.TextTertiary
            )
        }
    }
}

/** 面包屑分隔符。 */
private const val BREADCRUMB_SEPARATOR = " / "

/** 文件夹行尾指示符（非中文，避免 C5 约束）。 */
private const val FOLDER_INDICATOR = ">"