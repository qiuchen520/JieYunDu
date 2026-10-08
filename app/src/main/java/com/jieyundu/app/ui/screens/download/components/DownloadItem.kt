// 文件：DownloadItem.kt
// 职责：下载列表单项玻璃卡片（文件名 / 进度条 / 详情行 / 暂停继续 / 分享安装 / 删除），并做错开淡入动画
// 依赖：GlassCard、FileSizeFormatter、DownloadTimeFormatter、Dimens、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.download.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.jieyundu.app.R
import com.jieyundu.app.domain.downloader.DownloadProgressState
import com.jieyundu.app.domain.downloader.DownloadState
import com.jieyundu.app.domain.util.DownloadTimeFormatter
import com.jieyundu.app.domain.util.FileSizeFormatter
import com.jieyundu.app.ui.icons.TrashGlyph
import com.jieyundu.app.ui.icons.ShareGlyph
import com.jieyundu.app.ui.icons.InstallGlyph
import com.jieyundu.app.ui.glass.GlassCard
import com.jieyundu.app.ui.screens.download.DownloadListItem
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors
import kotlinx.coroutines.delay

/** 列表项错开淡入间隔（9.7：依次错开 50ms）。 */
private const val STAGGER_DELAY_MILLIS = 50L

/** 出现动画阻尼比（临界阻尼，避免透明度过冲）。 */
private const val APPEAR_DAMPING = 1f

/** 出现动画刚度。 */
private const val APPEAR_STIFFNESS = 380f

/** 百分比换算为 0f..1f 比例时的除数。 */
private const val PERCENT_SCALE = 100f

/**
 * 下载列表单项（9.6.4 + 布局修订 + JYD-DLSPEED-2026-10-04）。
 *
 * 布局（自顶向下三行，对应 Owner「整体进度」要求）：
 * 1. 文件名（14sp）＋ 状态文案；
 * 2. 进度条（6dp / 圆角 3dp，自绘）＋ 百分比（13sp）；
 * 3. 详情行：已下载 / 总量 · 速度 · 剩余时间（14sp）。
 *
 * 说明：
 * - 进度条按 9.6.4 规格自绘（底色 [JieYunDuColors.ProgressTrack]、填充 [JieYunDuColors.ProgressFill]），
 *   未使用 Material 默认 `LinearProgressIndicator`：视觉规格完全一致（6dp / 圆角 3dp），
 *   同时满足 9.9「不要直接用 Material 默认组件堆叠」的约定；
 * - 行尾按钮：暂停 / 继续（仅非终态展示，文字按钮，不引入图标依赖）、分享 / 安装（仅完成态）、
 *   删除（始终展示）；
 * - 出现时按 [index] 错开 50ms 淡入并上移 12dp（9.7）。
 *
 * @param item 列表条目（进度 + 文件名 + 落盘路径）。
 * @param onClick 点击回调（由上层决定暂停 / 继续）。
 * @param onDelete 点击删除按钮的回调（删除任务与本地文件）。
 * @param onToggle 点击暂停 / 继续按钮的回调。
 * @param onRestart 点击「重新下载」按钮的回调（清空分片后从零开始；仅暂停 / 失败态展示）。
 * @param onShare 点击分享按钮的回调（B2 功能③；仅已完成条目展示）。
 * @param onInstall 点击安装按钮的回调；为 null 表示不展示安装按钮（非 APK）。
 * @param modifier 外部修饰符。
 * @param isExpanded true 表示平板尺寸档位，false 表示手机档位。
 * @param index 在列表中的下标，用于错开动画。
 */
@Composable
fun DownloadItem(
    item: DownloadListItem,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onToggle: () -> Unit = {},
    onRestart: () -> Unit = {},
    onShare: () -> Unit = {},
    onInstall: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = true,
    index: Int = 0
) {
    val itemHeight: Dp =
        if (isExpanded) Dimens.DownloadItemHeight else Dimens.DownloadItemHeightCompact
    val contentPadding: Dp = if (isExpanded) Dimens.PanelPadding else Dimens.SpaceMd
    // 三行信息的行间距：手机档位更紧凑（SpaceXs），平板档位留白更足（SpaceSm）。
    val rowSpacing: Dp = if (isExpanded) Dimens.SpaceSm else Dimens.SpaceXs
    val appearOffsetPx = with(LocalDensity.current) { Dimens.SpaceMd.toPx() }
    val appear = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (index > 0) {
            delay(index * STAGGER_DELAY_MILLIS)
        }
        appear.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = APPEAR_DAMPING, stiffness = APPEAR_STIFFNESS)
        )
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight)
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * appearOffsetPx
            },
        contentPadding = contentPadding,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                FileNameRow(item = item)
                Spacer(modifier = Modifier.height(rowSpacing))
                ProgressRow(item = item, isExpanded = isExpanded)
                Spacer(modifier = Modifier.height(rowSpacing))
                DetailRow(item = item)
            }
            // 重新下载（【JYD-P1-2026-10-04】P1-2）：仅暂停 / 失败态展示，
            // 与「继续」并列，保证「续传失败时还有一条明确出路」。
            if (item.progress.state == DownloadState.PAUSED ||
                item.progress.state == DownloadState.FAILED
            ) {
                Spacer(modifier = Modifier.width(Dimens.SpaceSm))
                ToggleButton(
                    label = stringResource(R.string.download_action_restart),
                    onClick = onRestart
                )
            }
            // 暂停 / 继续（Owner 反馈：下载项必须有显式的暂停按钮，不能只靠点整卡）。
            if (item.progress.state == DownloadState.DOWNLOADING ||
                item.progress.state == DownloadState.PENDING ||
                item.progress.state == DownloadState.PAUSED
            ) {
                Spacer(modifier = Modifier.width(Dimens.SpaceMd))
                ToggleButton(
                    label = stringResource(
                        if (item.progress.state == DownloadState.DOWNLOADING) {
                            R.string.download_action_pause
                        } else {
                            R.string.download_action_resume
                        }
                    ),
                    onClick = onToggle
                )
            }
            // 功能③：仅「已完成」条目展示分享 / 安装按钮（APK 才显示安装）。
            if (item.progress.state == DownloadState.COMPLETED) {
                Spacer(modifier = Modifier.width(Dimens.SpaceMd))
                ShareButton(onClick = onShare)
                if (onInstall != null) {
                    Spacer(modifier = Modifier.width(Dimens.SpaceSm))
                    InstallButton(onClick = onInstall)
                }
            }
            Spacer(modifier = Modifier.width(Dimens.SpaceMd))
            DeleteButton(onClick = onDelete)
        }
    }
}

/**
 * 首行：文件名 + 状态文案。
 *
 * @param item 列表条目。
 */
@Composable
private fun FileNameRow(item: DownloadListItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.fileName ?: stringResource(R.string.download_task_untitled),
            style = MaterialTheme.typography.labelSmall,
            color = JieYunDuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(Dimens.SpaceSm))
        Text(
            text = stringResource(stateLabelRes(item.progress.state)),
            style = MaterialTheme.typography.labelMedium,
            color = JieYunDuColors.TextMuted,
            maxLines = 1
        )
    }
}

/**
 * 第二行：进度条 + 百分比。
 *
 * 说明：进度条本体按 9.6.4 规格，高度固定 6dp、圆角 3dp；百分比按 9.6.4 既有
 * `download_progress_format`（`%1$d%%`）展示，落在进度条右侧。
 *
 * @param item 列表条目。
 * @param isExpanded true 表示平板档位（百分比字号更大）。
 */
@Composable
private fun ProgressRow(item: DownloadListItem, isExpanded: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProgressTrack(
            fraction = item.progress.percent / PERCENT_SCALE,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(Dimens.SpaceMd))
        Text(
            text = stringResource(R.string.download_progress_format, item.progress.percent),
            style = if (isExpanded) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = JieYunDuColors.TextMuted,
            maxLines = 1
        )
    }
}

/**
 * 第三行：已下载 / 总量 · 速度 · 剩余时间。
 *
 * 说明：已下载与总量**必定展示**（Owner「整体进度」要求）；速度与剩余时间在空间不足时
 * 由 [TextOverflow.Ellipsis] 省略，不会把「已下载 / 总量」挤掉（其文本在最左侧）。
 *
 * @param item 列表条目。
 */
@Composable
private fun DetailRow(item: DownloadListItem) {
    val remaining = DownloadTimeFormatter.formatRemaining(
        DownloadTimeFormatter.remainingSeconds(
            remainingBytes = remainingBytes(item),
            speedBytesPerSecond = item.progress.speedBytesPerSecond
        )
    )
    Text(
        text = stringResource(
            R.string.download_detail_format,
            FileSizeFormatter.format(item.progress.downloadedBytes),
            FileSizeFormatter.format(item.progress.totalBytes),
            FileSizeFormatter.formatSpeed(item.progress.speedBytesPerSecond),
            remaining
        ),
        style = MaterialTheme.typography.labelSmall,
        color = JieYunDuColors.TextFaint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 文字按钮（暂停 / 继续）。
 *
 * 说明（D8）：不引入 `material-icons`，用语义化文字按钮更易发现（Owner 反馈「找不到暂停」）。
 * 视觉沿用既有玻璃配色：浅主色底 + 主色字，与删除 / 分享圆形按钮同系。
 *
 * @param label 按钮文案。
 * @param onClick 点击回调。
 */
@Composable
private fun ToggleButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(Dimens.ToggleButtonHeight)
            .clip(RoundedCornerShape(Dimens.ButtonCornerCompact))
            .background(JieYunDuColors.IconButtonFill)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = JieYunDuColors.Primary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Dimens.SpaceMd)
        )
    }
}

/**
 * 圆形删除按钮（自绘垃圾桶图标，D8：不引入 material-icons）。
 *
 * @param onClick 点击回调。
 */
@Composable
private fun DeleteButton(onClick: () -> Unit) {
    val label = stringResource(R.string.cd_delete_download)
    Box(
        modifier = Modifier
            .size(Dimens.DeleteButtonSize)
            .clip(CircleShape)
            .background(JieYunDuColors.IconButtonFill)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        TrashGlyph(modifier = Modifier.size(Dimens.SpaceXxl))
    }
}

/**
 * 圆形分享按钮（B2 功能③；D8：自绘三节点分享图标，不引入 material-icons）。
 *
 * @param onClick 点击回调。
 */
@Composable
private fun ShareButton(onClick: () -> Unit) {
    val label = stringResource(R.string.cd_share_download)
    Box(
        modifier = Modifier
            .size(Dimens.DeleteButtonSize)
            .clip(CircleShape)
            .background(JieYunDuColors.IconButtonFill)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ShareGlyph(modifier = Modifier.size(Dimens.SpaceXxl))
    }
}

/**
 * 圆形安装按钮（B2 功能③；仅 APK 条目展示）。
 *
 * @param onClick 点击回调。
 */
@Composable
private fun InstallButton(onClick: () -> Unit) {
    val label = stringResource(R.string.cd_install_download)
    Box(
        modifier = Modifier
            .size(Dimens.DeleteButtonSize)
            .clip(CircleShape)
            .background(JieYunDuColors.IconButtonFill)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        InstallGlyph(modifier = Modifier.size(Dimens.SpaceXxl))
    }
}


/**
 * 自绘进度条（9.6.4：高 6dp、圆角 3dp，底色浅灰、填充主色蓝）。
 *
 * @param fraction 进度比例，取值 0f..1f（越界自动收敛）。
 * @param modifier 外部修饰符（由调用方负责横向占位）。
 */
@Composable
private fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(Dimens.ProgressBarHeight)
            .clip(RoundedCornerShape(Dimens.ProgressBarCorner))
            .background(JieYunDuColors.ProgressTrack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(safeFraction)
                .fillMaxHeight()
                .background(JieYunDuColors.ProgressFill)
        )
    }
}

/**
 * 计算剩余字节数（总量 − 已下载）。
 *
 * 说明：总量未知（[DownloadProgressState.UNKNOWN_SIZE]）时返回 -1，
 * 由 [DownloadTimeFormatter] 统一渲染为 `--`（【JYD-DLSPEED-2026-10-04】）。
 *
 * @param item 列表条目。
 * @return 剩余字节数；总量未知时返回 -1。
 */
private fun remainingBytes(item: DownloadListItem): Long =
    if (item.progress.totalBytes <= 0L) {
        DownloadProgressState.UNKNOWN_SIZE
    } else {
        (item.progress.totalBytes - item.progress.downloadedBytes).coerceAtLeast(0L)
    }

/**
 * 下载状态 → 文案资源 id。
 *
 * @param state 离散状态。
 * @return 对应的 strings.xml 文案资源 id。
 */
@StringRes
private fun stateLabelRes(state: DownloadState): Int = when (state) {
    DownloadState.PENDING -> R.string.download_state_pending
    DownloadState.DOWNLOADING -> R.string.download_state_downloading
    DownloadState.PAUSED -> R.string.download_state_paused
    DownloadState.COMPLETED -> R.string.download_state_completed
    DownloadState.FAILED -> R.string.download_state_failed
    DownloadState.CANCELED -> R.string.download_state_canceled
}
