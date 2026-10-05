// 文件：ParseResultCard.kt
// 职责：解析结果展示卡片（空闲提示 / 解析中 / 目录浏览（展开·返回上一级） / 需要提取码 / 失败）
// 依赖：GlassCard、GlassButton、FileSizeFormatter、ParseResult、BrowseLevel、Dimens、JieYunDuColors
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.jieyundu.app.R
import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.ParseResult
import com.jieyundu.app.domain.util.FileSizeFormatter
import com.jieyundu.app.ui.glass.GlassButton
import com.jieyundu.app.ui.glass.GlassCard
import com.jieyundu.app.ui.screens.home.BrowseLevel
import com.jieyundu.app.ui.screens.home.parseErrorLabelRes
import com.jieyundu.app.ui.screens.home.uiLabelRes
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 解析结果卡片（9.6.3 + 布局修订 + 阶段 13 目录浏览）。
 *
 * 说明：按 [ParseResult] 分支渲染；成功时按**当前目录层** [level] 渲染条目——
 * 文件夹可点击进入（[onOpenFolder]）、普通文件可勾选后批量下载（[onDownload]）；
 * 非根目录时展示面包屑与「返回上一级」（[onNavigateUp]）。
 * 卡片整体点击缩放 0.98 回弹由 [GlassCard] 提供（9.6.3）。
 *
 * @param result 解析结果；未解析时为 null。
 * @param errorRes 本地校验错误的文案资源；无错误时为 null。
 * @param isParsing 是否正在解析。
 * @param level 当前浏览的目录层；为 null 时回落展示 [ParseResult.Success.files]。
 * @param breadcrumb 由路径栈拼接的面包屑文本（根目录仅有分享名）。
 * @param canNavigateUp 是否可以返回上一级（栈深大于 1）。
 * @param isLoadingDir 是否正在展开子目录。
 * @param dirErrorRes 展开子目录失败的文案资源；无错误时为 null。
 * @param isPreparingDownload 是否正在转存并换取直链（下载前的准备阶段）。
 * @param downloadErrorRes 下载启动失败的文案资源；无错误时为 null。
 * @param onDownload 点击「下载选中」的回调，参数为本次勾选的文件列表。
 * @param onOpenFolder 点击文件夹进入下一级的回调。
 * @param onNavigateUp 点击「返回上一级」的回调。
 * @param modifier 外部修饰符。
 * @param isExpanded true 表示平板尺寸档位，false 表示手机档位。
 */
@Composable
fun ParseResultCard(
    result: ParseResult?,
    @StringRes errorRes: Int?,
    isParsing: Boolean,
    level: BrowseLevel?,
    breadcrumb: String,
    canNavigateUp: Boolean,
    isLoadingDir: Boolean,
    @StringRes dirErrorRes: Int?,
    isPreparingDownload: Boolean,
    @StringRes downloadErrorRes: Int?,
    onDownload: (List<FileInfo>) -> Unit,
    onOpenFolder: (FileInfo) -> Unit,
    onNavigateUp: () -> Unit,
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
            level = level,
            breadcrumb = breadcrumb,
            canNavigateUp = canNavigateUp,
            isLoadingDir = isLoadingDir,
            dirErrorRes = dirErrorRes,
            isPreparingDownload = isPreparingDownload,
            downloadErrorRes = downloadErrorRes,
            onDownload = onDownload,
            onOpenFolder = onOpenFolder,
            onNavigateUp = onNavigateUp,
            modifier = modifier,
            contentPadding = contentPadding
        )

        result is ParseResult.NeedPassword -> NeedPasswordCard(
            modifier = modifier,
            contentPadding = contentPadding
        )

        result is ParseResult.Error -> {
            val label = stringResource(parseErrorLabelRes(result.code))
            // 服务端消息透传（JYD-BAIDU-ERRNO-2026-10-05）：message 与 code 不同即为服务端原文
            // （如 err_errmsg / show_msg），另起一行展示，让用户看到精确原因（例如提取码错误）。
            val detail = result.message.takeIf { value -> value.isNotBlank() && value != result.code }
            InfoCard(
                text = if (detail == null) {
                    stringResource(R.string.parse_error_format, label)
                } else {
                    stringResource(R.string.parse_error_format, label) + "\n" + detail
                },
                modifier = modifier,
                contentPadding = contentPadding
            )
        }

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
 * 解析成功卡片：头部展示网盘类型，其后为面包屑 / 返回上一级，下方为当前目录条目。
 *
 * 关键行为（阶段 13）：
 * - 文件夹条目显示「文件夹」而非 `0 B`，点击即进入下一级；
 * - 普通文件条目保持勾选 + 批量下载；
 * - 非根目录时提供「返回上一级」。
 *
 * @param result 解析成功结果（仅取其网盘类型与根目录条目兜底）。
 * @param level 当前目录层；为 null 时回落到根目录 [ParseResult.Success.files]。
 * @param breadcrumb 面包屑文本。
 * @param canNavigateUp 是否可以返回上一级。
 * @param isLoadingDir 是否正在展开子目录。
 * @param dirErrorRes 展开子目录失败的文案资源。
 * @param isPreparingDownload 是否正在转存并换取直链（下载准备阶段）。
 * @param downloadErrorRes 下载启动失败的文案资源；无错误时为 null。
 * @param onDownload 下载回调，参数为本次勾选的文件列表。
 * @param onOpenFolder 进入文件夹回调。
 * @param onNavigateUp 返回上一级回调。
 * @param modifier 外部修饰符。
 * @param contentPadding 卡内边距。
 */
@Composable
private fun SuccessCard(
    result: ParseResult.Success,
    level: BrowseLevel?,
    breadcrumb: String,
    canNavigateUp: Boolean,
    isLoadingDir: Boolean,
    @StringRes dirErrorRes: Int?,
    isPreparingDownload: Boolean,
    @StringRes downloadErrorRes: Int?,
    onDownload: (List<FileInfo>) -> Unit,
    onOpenFolder: (FileInfo) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: Dp = Dimens.PanelPadding
) {
    val files = level?.files ?: result.files
    // 勾选状态按「目录层」隔离：切换目录时重置，避免跨层误选。
    val levelKey = level?.pdirFid ?: ROOT_LEVEL_KEY
    val selectedFids = remember(levelKey) { mutableStateListOf<String>() }
    val selectedFiles = files.filter { !it.isDirectory && selectedFids.contains(it.fid) }
    val hasSelection = selectedFiles.isNotEmpty()
    val showDownloadButton = files.isNotEmpty() && !isLoadingDir && dirErrorRes == null

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Dimens.CardCorner,
        contentPadding = contentPadding
    ) {
        // 结构（Owner 4.1 / 4.2）：头部固定 + 文件列表 LazyColumn 可滚动 + 下载按钮底部悬浮。
        // 外层 Box 由 GlassCard 提供；Column 撑满卡片，LazyColumn 以 weight 占满余下高度。
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
        ) {
            Text(
                text = stringResource(result.netdiskType.uiLabelRes()),
                style = MaterialTheme.typography.titleMedium,
                color = JieYunDuColors.TextPrimary
            )
            if (breadcrumb.isNotBlank()) {
                Text(
                    text = breadcrumb,
                    style = MaterialTheme.typography.labelSmall,
                    color = JieYunDuColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (canNavigateUp) {
                Text(
                    text = stringResource(R.string.parse_browse_up),
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.SpaceSm))
                        .clickable(onClick = onNavigateUp)
                        .padding(vertical = Dimens.SpaceXs, horizontal = Dimens.SpaceXs),
                    style = MaterialTheme.typography.labelLarge,
                    color = JieYunDuColors.Primary
                )
            }
            if (isPreparingDownload) {
                Text(
                    text = stringResource(R.string.parse_download_preparing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JieYunDuColors.TextSecondary
                )
            }
            if (downloadErrorRes != null) {
                Text(
                    text = stringResource(downloadErrorRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JieYunDuColors.TextSecondary
                )
            }
            when {
                isLoadingDir -> Text(
                    text = stringResource(R.string.parse_loading_dir),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JieYunDuColors.TextSecondary
                )

                dirErrorRes != null -> Text(
                    text = stringResource(dirErrorRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JieYunDuColors.TextSecondary
                )

                files.isEmpty() -> Text(
                    text = stringResource(R.string.parse_browse_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JieYunDuColors.TextSecondary
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
                    contentPadding = PaddingValues(bottom = Dimens.DownloadButtonInset)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.parse_result_select_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = JieYunDuColors.TextTertiary
                        )
                    }
                    items(files) { file ->
                        val checked = selectedFids.contains(file.fid)
                        FileEntryRow(
                            file = file,
                            checked = checked,
                            onToggle = {
                                if (file.isDirectory) {
                                    onOpenFolder(file)
                                } else if (selectedFids.contains(file.fid)) {
                                    selectedFids.remove(file.fid)
                                } else {
                                    selectedFids.add(file.fid)
                                }
                            }
                        )
                    }
                }
            }
        }
        if (showDownloadButton) {
            GlassButton(
                text = if (hasSelection) {
                    stringResource(R.string.parse_download_count, selectedFiles.size)
                } else {
                    stringResource(R.string.action_download)
                },
                onClick = { if (hasSelection) onDownload(selectedFiles) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = if (hasSelection) 1f else DISABLED_BUTTON_ALPHA
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

/**
 * 单条目录条目行：文件夹显示文件夹图标与「文件夹」字样并可点击进入；
 * 普通文件显示勾选框、文件名与大小。
 *
 * @param file 文件条目。
 * @param checked 是否已勾选（仅普通文件有意义）。
 * @param onToggle 点击行回调（文件夹进入 / 文件切换勾选）。
 */
@Composable
private fun FileEntryRow(
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
        if (file.isDirectory) {
            FolderGlyph(modifier = Modifier.size(Dimens.CheckboxSize))
        } else {
            SelectionBox(checked = checked)
        }
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
                text = if (file.isDirectory) {
                    stringResource(R.string.parse_item_folder)
                } else {
                    FileSizeFormatter.format(file.fileSize)
                },
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
 * 自绘「文件夹」图标（D8：不引入 material-icons）。
 *
 * @param modifier 外部修饰符。
 */
@Composable
private fun FolderGlyph(modifier: Modifier = Modifier) {
    val color = JieYunDuColors.Primary
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val tabTop = h * FOLDER_TAB_TOP_Y
        val tabWidth = w * FOLDER_TAB_WIDTH_RATIO
        val tabHeight = h * FOLDER_TAB_HEIGHT_RATIO
        val corner = w * FOLDER_CORNER_RATIO
        drawRoundRect(
            color = color,
            topLeft = Offset(w * FOLDER_TAB_LEFT_X, tabTop),
            size = Size(tabWidth, tabHeight),
            cornerRadius = CornerRadius(corner, corner)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * FOLDER_BODY_LEFT_X, tabTop + tabHeight * FOLDER_BODY_TOP_OFFSET),
            size = Size(w * FOLDER_BODY_WIDTH_RATIO, h * FOLDER_BODY_HEIGHT_RATIO),
            cornerRadius = CornerRadius(corner, corner)
        )
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

/** 勾选状态在无目录层时使用的兜底键。 */
private const val ROOT_LEVEL_KEY = "__root__"

/** 未选中任何文件时下载按钮的透明度（置灰表现）。 */
private const val DISABLED_BUTTON_ALPHA = 0.45f

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

/** 文件夹图标：标签顶边纵向占比。 */
private const val FOLDER_TAB_TOP_Y = 0.14f

/** 文件夹图标：标签左边横向占比。 */
private const val FOLDER_TAB_LEFT_X = 0.08f

/** 文件夹图标：标签宽度占比。 */
private const val FOLDER_TAB_WIDTH_RATIO = 0.44f

/** 文件夹图标：标签高度占比。 */
private const val FOLDER_TAB_HEIGHT_RATIO = 0.20f

/** 文件夹图标：圆角相对宽度比例。 */
private const val FOLDER_CORNER_RATIO = 0.10f

/** 文件夹图标：主体左边横向占比。 */
private const val FOLDER_BODY_LEFT_X = 0.06f

/** 文件夹图标：主体宽度占比。 */
private const val FOLDER_BODY_WIDTH_RATIO = 0.88f

/** 文件夹图标：主体高度占比。 */
private const val FOLDER_BODY_HEIGHT_RATIO = 0.60f

/** 文件夹图标：主体相对标签底部的纵向偏移比例。 */
private const val FOLDER_BODY_TOP_OFFSET = 0.60f
