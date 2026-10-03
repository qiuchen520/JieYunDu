// 文件：ParseResultCard.kt
// 职责：解析结果展示卡片（空闲提示 / 解析中 / 成功文件列表可选下载 / 需要提取码 / 失败）
// 依赖：GlassCard、GlassButton、FileSizeFormatter、ParseResult、Dimens、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.home.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.jieyundu.app.R
import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.ParseResult
import com.jieyundu.app.domain.util.FileSizeFormatter
import com.jieyundu.app.ui.glass.GlassButton
import com.jieyundu.app.ui.glass.GlassCard
import com.jieyundu.app.ui.screens.home.parseErrorLabelRes
import com.jieyundu.app.ui.screens.home.uiLabelRes
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 解析结果卡片（9.6.3 + 布局修订）。
 *
 * 说明：按 [ParseResult] 分支渲染；成功时可勾选文件并点击「下载选中」批量投递下载。
 * 卡片整体点击缩放 0.98 回弹由 [GlassCard] 提供（9.6.3）。
 *
 * @param result 解析结果；未解析时为 null。
 * @param errorRes 本地校验错误的文案资源；无错误时为 null。
 * @param isParsing 是否正在解析。
 * @param onDownload 点击下载单个文件的回调。
 * @param modifier 外部修饰符。
 * @param isExpanded true 表示平板尺寸档位，false 表示手机档位。
 */
@Composable
fun ParseResultCard(
    result: ParseResult?,
    @StringRes errorRes: Int?,
    isParsing: Boolean,
    onDownload: (FileInfo) -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = true
) {
    val contentPadding: Dp =
        if (isExpanded) Dimens.PanelPadding else Dimens.PanelPaddingCompact
    when {
        isParsing -> InfoCard(
            text = stringResource(R.string.parse_parsing),
            modifier = modifier,
            contentPadding = contentPadding
        )

        errorRes != null -> InfoCard(
            text = stringResource(errorRes),
            modifier = modifier,
            contentPadding = contentPadding
        )

        result is ParseResult.Success -> SuccessCard(
            result = result,
            onDownload = onDownload,
            modifier = modifier,
            contentPadding = contentPadding
        )

        result is ParseResult.NeedPassword -> NeedPasswordCard(
            modifier = modifier,
            contentPadding = contentPadding
        )

        result is ParseResult.Error -> InfoCard(
            text = stringResource(
                R.string.parse_error_format,
                stringResource(parseErrorLabelRes(result.code))
            ),
            modifier = modifier,
            contentPadding = contentPadding
        )

        else -> IdleHint(modifier = modifier)
    }
}

/**
 * 空闲态提示卡（尚未解析时的占位）。
 *
 * @param modifier 外部修饰符。
 */
@Composable
private fun IdleHint(modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Dimens.CardCorner,
        contentPadding = Dimens.PanelPadding
    ) {
        Text(
            text = stringResource(R.string.parse_result_idle),
            style = MaterialTheme.typography.bodyMedium,
            color = JieYunDuColors.TextTertiary
        )
    }
}

/**
 * 纯文本信息卡（解析中 / 本地错误 / 解析失败共用）。
 *
 * @param text 展示文案。
 * @param modifier 外部修饰符。
 * @param contentPadding 卡内边距。
 */
@Composable
private fun InfoCard(
    text: String,
    modifier: Modifier = Modifier,
    contentPadding: Dp = Dimens.PanelPadding
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Dimens.CardCorner,
        contentPadding = contentPadding
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = JieYunDuColors.TextSecondary
        )
    }
}

/**
 * 解析成功卡片：头部展示网盘类型，下方为可勾选的文件列表与「下载选中」按钮。
 *
 * @param result 解析成功结果。
 * @param onDownload 下载回调（逐个文件投递）。
 * @param modifier 外部修饰符。
 * @param contentPadding 卡内边距。
 */
@Composable
private fun SuccessCard(
    result: ParseResult.Success,
    onDownload: (FileInfo) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: Dp = Dimens.PanelPadding
) {
    val selectedFids = remember { mutableStateListOf<String>() }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Dimens.CardCorner,
        contentPadding = contentPadding
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
        ) {
            Text(
                text = stringResource(result.netdiskType.uiLabelRes()),
                style = MaterialTheme.typography.titleMedium,
                color = JieYunDuColors.TextPrimary
            )
            if (result.files.isEmpty()) {
                Text(
                    text = stringResource(R.string.parse_result_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JieYunDuColors.TextSecondary
                )
            } else {
                Text(
                    text = stringResource(R.string.parse_result_select_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = JieYunDuColors.TextTertiary
                )
                result.files.forEach { file ->
                    SelectableFileRow(
                        file = file,
                        checked = selectedFids.contains(file.fid),
                        onToggle = {
                            if (selectedFids.contains(file.fid)) {
                                selectedFids.remove(file.fid)
                            } else {
                                selectedFids.add(file.fid)
                            }
                        }
                    )
                }
                GlassButton(
                    text = stringResource(R.string.action_download),
                    onClick = {
                        result.files
                            .filter { selectedFids.contains(it.fid) }
                            .forEach(onDownload)
                    },
                    height = Dimens.ButtonHeightCompact,
                    cornerRadius = Dimens.ButtonCornerCompact,
                    fillColor = JieYunDuColors.ButtonFill,
                    borderColor = JieYunDuColors.ButtonBorder,
                    contentColor = JieYunDuColors.OnPrimary
                )
            }
        }
    }
}

/**
 * 可勾选的单条文件行：左侧勾选框，右侧文件名与大小。
 *
 * @param file 文件条目。
 * @param checked 是否已勾选。
 * @param onToggle 切换勾选回调。
 */
@Composable
private fun SelectableFileRow(
    file: FileInfo,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.SpaceSm))
            .clickable(onClick = onToggle)
            .padding(vertical = Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SelectionBox(checked = checked)
        Spacer(modifier = Modifier.width(Dimens.SpaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.fileName,
                style = MaterialTheme.typography.titleMedium,
                color = JieYunDuColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = FileSizeFormatter.format(file.fileSize),
                style = MaterialTheme.typography.labelSmall,
                color = JieYunDuColors.TextTertiary
            )
        }
    }
}

/**
 * 自绘勾选框（选中为主色蓝底 + 白勾）。
 *
 * @param checked 是否选中。
 */
@Composable
private fun SelectionBox(checked: Boolean) {
    val shape = RoundedCornerShape(Dimens.SpaceXs)
    Box(
        modifier = Modifier
            .size(Dimens.CheckboxSize)
            .clip(shape)
            .background(if (checked) JieYunDuColors.Primary else JieYunDuColors.InputFieldFill)
            .border(
                width = Dimens.HighlightStroke,
                color = if (checked) JieYunDuColors.Primary else JieYunDuColors.GlassBorder,
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            CheckGlyph(modifier = Modifier.size(Dimens.SpaceMd))
        }
    }
}

/**
 * 自绘「对勾」图标（D8：不引入 material-icons）。
 *
 * @param modifier 外部修饰符。
 */
@Composable
private fun CheckGlyph(modifier: Modifier = Modifier) {
    val color = JieYunDuColors.OnPrimary
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawLine(
            color = color,
            start = Offset(w * CHECK_START_X, h * CHECK_MID_Y),
            end = Offset(w * CHECK_MID_X, h * CHECK_BOTTOM_Y),
            strokeWidth = w * CHECK_STROKE_RATIO,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * CHECK_MID_X, h * CHECK_BOTTOM_Y),
            end = Offset(w * CHECK_END_X, h * CHECK_TOP_Y),
            strokeWidth = w * CHECK_STROKE_RATIO,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 需要提取码卡片。
 *
 * @param modifier 外部修饰符。
 * @param contentPadding 卡内边距。
 */
@Composable
private fun NeedPasswordCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = Dimens.PanelPadding
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Dimens.CardCorner,
        contentPadding = contentPadding
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
        ) {
            Text(
                text = stringResource(R.string.parse_need_password),
                style = MaterialTheme.typography.titleMedium,
                color = JieYunDuColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.parse_need_password_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = JieYunDuColors.TextSecondary
            )
        }
    }
}

/** 对勾起点横向占比。 */
private const val CHECK_START_X = 0.15f

/** 对勾折点横向占比。 */
private const val CHECK_MID_X = 0.42f

/** 对勾终点横向占比。 */
private const val CHECK_END_X = 0.85f

/** 对勾起点纵向占比。 */
private const val CHECK_TOP_Y = 0.25f

/** 对勾折点纵向占比。 */
private const val CHECK_MID_Y = 0.55f

/** 对勾终点纵向占比。 */
private const val CHECK_BOTTOM_Y = 0.78f

/** 对勾线宽相对宽度比例。 */
private const val CHECK_STROKE_RATIO = 0.14f