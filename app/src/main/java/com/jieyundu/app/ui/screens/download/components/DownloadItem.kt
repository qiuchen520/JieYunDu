// 文件：DownloadItem.kt
// 职责：下载列表单项玻璃卡片（文件名 / 速度 / 进度条 / 状态 / 删除按钮），并做错开淡入动画
// 依赖：GlassCard、FileSizeFormatter、Dimens、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.download.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.jieyundu.app.R
import com.jieyundu.app.domain.downloader.DownloadState
import com.jieyundu.app.domain.util.FileSizeFormatter
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

/**
 * 下载列表单项（9.6.4 + 布局修订）。
 *
 * 说明：进度条用 Box 双层自绘（浅灰轨道 + 主色蓝填充），不使用 Material 默认
 * LinearProgressIndicator（R5 / D3）；出现时按 [index] 错开 50ms 淡入并上移 12dp（9.7）。
 * 布局修订：行尾新增圆形「删除」按钮，点击删除任务及其本地文件（不影响整卡暂停/继续点击）。
 *
 * @param item 列表条目（进度 + 文件名 + 落盘路径）。
 * @param onClick 点击回调（由上层决定暂停 / 继续）。
 * @param onDelete 点击删除按钮的回调（删除任务与本地文件）。
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
    onShare: () -> Unit = {},
    onInstall: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = true,
    index: Int = 0
) {
    val itemHeight: Dp =
        if (isExpanded) Dimens.DownloadItemHeight else Dimens.DownloadItemHeightCompact
    val contentPadding: Dp = if (isExpanded) Dimens.PanelPadding else Dimens.SpaceMd
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
                        text = stringResource(
                            R.string.download_speed_pair_format,
                            FileSizeFormatter.formatSpeed(item.progress.speedBytesPerSecond),
                            FileSizeFormatter.formatSpeed(item.progress.averageSpeedBytesPerSecond)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = JieYunDuColors.TextFaint
                    )
                }
                Spacer(modifier = Modifier.height(Dimens.SpaceSm))
                ProgressTrack(fraction = item.progress.percent / PERCENT_SCALE)
                Spacer(modifier = Modifier.height(Dimens.SpaceSm))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            R.string.download_size_format,
                            FileSizeFormatter.format(item.progress.downloadedBytes),
                            FileSizeFormatter.format(item.progress.totalBytes)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = JieYunDuColors.TextFaint
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(stateLabelRes(item.progress.state)),
                        style = MaterialTheme.typography.labelSmall,
                        color = JieYunDuColors.TextMuted
                    )
                }
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
 * 自绘「分享」图标（三个节点 + 两条连线）。
 *
 * @param modifier 外部修饰符。
 */
@Composable
private fun ShareGlyph(modifier: Modifier = Modifier) {
    val color = JieYunDuColors.TextSecondary
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * SHARE_STROKE_RATIO
        val left = Offset(w * SHARE_LEFT_X, h * SHARE_MID_Y)
        val topRight = Offset(w * SHARE_RIGHT_X, h * SHARE_TOP_Y)
        val bottomRight = Offset(w * SHARE_RIGHT_X, h * SHARE_BOTTOM_Y)
        drawLine(color, left, topRight, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, left, bottomRight, strokeWidth = stroke, cap = StrokeCap.Round)
        val nodeRadius = w * SHARE_NODE_RADIUS_RATIO
        drawCircle(color, radius = nodeRadius, center = left)
        drawCircle(color, radius = nodeRadius, center = topRight)
        drawCircle(color, radius = nodeRadius, center = bottomRight)
    }
}

/**
 * 自绘「安装 / 下载到托盘」图标（下箭头 + 底部托盘）。
 *
 * @param modifier 外部修饰符。
 */
@Composable
private fun InstallGlyph(modifier: Modifier = Modifier) {
    val color = JieYunDuColors.TextSecondary
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * TRASH_STROKE_RATIO
        val centerX = w / 2f
        drawLine(
            color = color,
            start = Offset(centerX, h * INSTALL_TOP_Y),
            end = Offset(centerX, h * INSTALL_MID_Y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(centerX - w * INSTALL_WING_RATIO, h * (INSTALL_MID_Y - INSTALL_WING_RATIO)),
            end = Offset(centerX, h * INSTALL_MID_Y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(centerX + w * INSTALL_WING_RATIO, h * (INSTALL_MID_Y - INSTALL_WING_RATIO)),
            end = Offset(centerX, h * INSTALL_MID_Y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * INSTALL_BASE_START, h * INSTALL_BASE_Y),
            end = Offset(w * INSTALL_BASE_END, h * INSTALL_BASE_Y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 自绘「垃圾桶」图标。
 *
 * @param modifier 外部修饰符。
 */
@Composable
private fun TrashGlyph(modifier: Modifier = Modifier) {
    val color = JieYunDuColors.TextSecondary
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * TRASH_STROKE_RATIO
        // 桶盖
        drawLine(
            color = color,
            start = Offset(w * TRASH_LID_START, h * TRASH_LID_Y),
            end = Offset(w * TRASH_LID_END, h * TRASH_LID_Y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // 提手
        drawLine(
            color = color,
            start = Offset(w * TRASH_HANDLE_START, h * TRASH_HANDLE_Y),
            end = Offset(w * TRASH_HANDLE_END, h * TRASH_HANDLE_Y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // 桶身左壁
        drawLine(
            color = color,
            start = Offset(w * TRASH_BODY_LEFT_TOP, h * TRASH_LID_Y),
            end = Offset(w * TRASH_BODY_LEFT_BOTTOM, h * TRASH_BODY_BOTTOM),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // 桶身右壁
        drawLine(
            color = color,
            start = Offset(w * TRASH_BODY_RIGHT_TOP, h * TRASH_LID_Y),
            end = Offset(w * TRASH_BODY_RIGHT_BOTTOM, h * TRASH_BODY_BOTTOM),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // 桶底
        drawLine(
            color = color,
            start = Offset(w * TRASH_BODY_LEFT_BOTTOM, h * TRASH_BODY_BOTTOM),
            end = Offset(w * TRASH_BODY_RIGHT_BOTTOM, h * TRASH_BODY_BOTTOM),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 自绘进度条（9.6.4：高 6dp、圆角 3dp，底色浅灰、填充主色蓝）。
 *
 * @param fraction 进度比例，取值 0f..1f（越界自动收敛）。
 */
@Composable
private fun ProgressTrack(fraction: Float) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
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

/** 百分比换算为 0f..1f 比例时的除数。 */
private const val PERCENT_SCALE = 100f

/** 垃圾桶线宽相对宽度比例。 */
private const val TRASH_STROKE_RATIO = 0.09f

/** 桶盖起点横向占比。 */
private const val TRASH_LID_START = 0.16f

/** 桶盖终点横向占比。 */
private const val TRASH_LID_END = 0.84f

/** 桶盖纵向占比。 */
private const val TRASH_LID_Y = 0.28f

/** 提手起点横向占比。 */
private const val TRASH_HANDLE_START = 0.36f

/** 提手终点横向占比。 */
private const val TRASH_HANDLE_END = 0.64f

/** 提手纵向占比。 */
private const val TRASH_HANDLE_Y = 0.13f

/** 桶身左壁顶部横向占比。 */
private const val TRASH_BODY_LEFT_TOP = 0.26f

/** 桶身左壁底部横向占比。 */
private const val TRASH_BODY_LEFT_BOTTOM = 0.32f

/** 桶身右壁顶部横向占比。 */
private const val TRASH_BODY_RIGHT_TOP = 0.74f

/** 桶身右壁底部横向占比。 */
private const val TRASH_BODY_RIGHT_BOTTOM = 0.68f

/** 桶底纵向占比。 */
private const val TRASH_BODY_BOTTOM = 0.88f

/** 分享图标线宽相对宽度比例。 */
private const val SHARE_STROKE_RATIO = 0.07f

/** 分享图标左节点横向占比。 */
private const val SHARE_LEFT_X = 0.28f

/** 分享图标右节点横向占比。 */
private const val SHARE_RIGHT_X = 0.74f

/** 分享图标中间纵向占比。 */
private const val SHARE_MID_Y = 0.5f

/** 分享图标右上节点纵向占比。 */
private const val SHARE_TOP_Y = 0.22f

/** 分享图标右下节点纵向占比。 */
private const val SHARE_BOTTOM_Y = 0.78f

/** 分享图标节点半径相对宽度比例。 */
private const val SHARE_NODE_RADIUS_RATIO = 0.08f

/** 安装图标箭头竖线起点纵向占比。 */
private const val INSTALL_TOP_Y = 0.16f

/** 安装图标箭头交汇点纵向占比。 */
private const val INSTALL_MID_Y = 0.62f

/** 安装图标箭头两翼长度占比。 */
private const val INSTALL_WING_RATIO = 0.18f

/** 安装图标托盘底线纵向占比。 */
private const val INSTALL_BASE_Y = 0.86f

/** 安装图标托盘底线起点横向占比。 */
private const val INSTALL_BASE_START = 0.24f

/** 安装图标托盘底线终点横向占比。 */
private const val INSTALL_BASE_END = 0.76f