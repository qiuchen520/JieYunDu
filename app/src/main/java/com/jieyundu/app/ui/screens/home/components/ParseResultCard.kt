// 文件：ParseResultCard.kt
// 职责：解析结果展示卡片（解析中 / 成功文件列表 / 需要提取码 / 失败）
// 依赖：GlassCard、FileSizeFormatter、ParseResult、Dimens、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.home.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.jieyundu.app.ui.glass.GlassCard
import com.jieyundu.app.ui.screens.home.parseErrorLabelRes
import com.jieyundu.app.ui.screens.home.uiLabelRes
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 解析结果卡片（9.6.3）。
 *
 * 说明：按 [ParseResult] 的四个状态分支渲染；成功时列出文件并提供圆形下载按钮，
 * 卡片整体点击缩放 0.98 回弹由 [GlassCard] 提供（9.6.3）。
 *
 * @param result 解析结果；未解析时为 null。
 * @param errorRes 本地校验错误的文案资源；无错误时为 null。
 * @param isParsing 是否正在解析。
 * @param onDownload 点击文件下载按钮的回调。
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

        else -> Unit
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
 * 解析成功卡片：头部展示网盘类型，下方逐条列出文件。
 *
 * @param result 解析成功结果。
 * @param onDownload 下载回调。
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
                result.files.forEach { file ->
                    FileRow(file = file, onDownload = onDownload)
                }
            }
        }
    }
}

/**
 * 单条文件行：左侧文件名与大小，右侧圆形下载按钮。
 *
 * @param file 文件条目。
 * @param onDownload 下载回调。
 */
@Composable
private fun FileRow(
    file: FileInfo,
    onDownload: (FileInfo) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                text = FileSizeFormatter.format(file.fileSize),
                style = MaterialTheme.typography.labelSmall,
                color = JieYunDuColors.TextTertiary
            )
        }
        Spacer(modifier = Modifier.width(Dimens.SpaceLg))
        DownloadCircleButton(onClick = { onDownload(file) })
    }
}

/**
 * 圆形下载按钮（9.6.3：56dp，白色 20% 底，图标白色）。
 *
 * @param onClick 点击回调。
 */
@Composable
private fun DownloadCircleButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Dimens.DownloadButtonSize)
            .clip(CircleShape)
            .background(JieYunDuColors.IconButtonFill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        DownloadGlyph(modifier = Modifier.size(Dimens.SpaceXxl))
    }
}

/**
 * 自绘「向下箭头 + 底线」下载图标。
 *
 * 说明（D8）：不引入 material-icons-extended（不在第五部分技术栈内），
 * 以 Compose 原生 [Canvas] 绘制，避免额外依赖。
 *
 * @param modifier 外部修饰符。
 */
@Composable
private fun DownloadGlyph(modifier: Modifier = Modifier) {
    val color = JieYunDuColors.TextPrimary
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

/** 图标线宽相对边长比例。 */
private const val GLYPH_STROKE_RATIO = 0.09f

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
