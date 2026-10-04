// 文件：DownloadScreen.kt
// 职责：下载页——顶部筛选条（全部 / 下载中 / 已完成）+ 任务列表（玻璃卡片）与空态提示
// 依赖：DownloadViewModel、DownloadFilterBar、DownloadItem、WindowSizeHelper、Dimens
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.jieyundu.app.R
import com.jieyundu.app.ui.adaptive.rememberIsExpandedLayout
import com.jieyundu.app.ui.glass.GlassPanel
import com.jieyundu.app.ui.screens.download.components.DownloadFilterBar
import com.jieyundu.app.ui.screens.download.components.DownloadItem
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors
import java.io.File
import timber.log.Timber

/**
 * 下载页。
 *
 * 说明：数据来自 [DownloadViewModel.items]（引擎实时快照 × Room 进度 × 会话文件名/路径，按筛选档过滤）；
 * 无任务或筛选后为空时展示空态。列表项点击切换暂停 / 继续，行尾另有显式「暂停 / 继续」按钮
 * （【JYD-DLSPEED2-2026-10-04】Owner 反馈：暂停入口需一眼可见），末位删除按钮删除任务与本地文件。
 * 平板与手机使用不同的卡片间距（9.4 / 9.5）。
 *
 * @param modifier 外部修饰符。
 */
@Composable
fun DownloadScreen(modifier: Modifier = Modifier) {
    val viewModel: DownloadViewModel = hiltViewModel()
    val downloadItems by viewModel.items.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val messageRes by viewModel.message.collectAsState()
    val context = LocalContext.current
    val isExpanded = rememberIsExpandedLayout()
    val spacing = if (isExpanded) Dimens.SpaceXl else Dimens.SpaceMd

    // 【JYD-P1-2026-10-04】P1-2：续传 / 重下结果一次性提示（取用后清除，避免重复弹出）。
    LaunchedEffect(messageRes) {
        val res = messageRes ?: return@LaunchedEffect
        Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
        viewModel.consumeMessage()
    }

    Column(modifier = modifier.fillMaxSize()) {
        DownloadFilterBar(
            selected = filter,
            onSelect = viewModel::onFilterChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing, end = spacing, top = spacing)
        )
        if (downloadItems.isEmpty()) {
            // B2 整页玻璃化：空态同样落在一块玻璃面板上，与列表卡片同材质。
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(spacing),
                cornerRadius = Dimens.CardCorner,
                contentPadding = Dimens.PanelPadding,
                fillColor = JieYunDuColors.GlassFill,
                borderColor = JieYunDuColors.GlassBorder
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
                        onToggle = { viewModel.toggleTask(item) },
                        onRestart = { viewModel.restartTask(item) },
                        onDelete = { viewModel.deleteTask(item) },
                        onShare = { shareDownload(context, item) },
                        onInstall = if (isApkFile(item.fileName)) {
                            { installDownload(context, item) }
                        } else {
                            null
                        },
                        isExpanded = isExpanded,
                        index = index
                    )
                }
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

/**
 * 判断文件名是否为 APK（B2 功能③：仅 APK 条目展示安装按钮）。
 *
 * @param fileName 文件名；未知时为 null。
 * @return true 表示是 APK 安装包。
 */
private fun isApkFile(fileName: String?): Boolean =
    fileName?.endsWith(APK_EXTENSION, ignoreCase = true) == true

/**
 * 解析条目对应的可访问 Uri（分享 / 安装共用）。
 *
 * 说明（B2 功能①③）：已发布到公共下载目录的成品在 Android 10+ 上是 `content://`，可直接使用；
 * 自定义目录与 `<29` 场景是真实路径，经 [FileProvider] 换取可授权 Uri。
 *
 * @param context 上下文。
 * @param item 列表条目（含落盘位置）。
 * @return 可访问 Uri；文件不存在或无法解析时返回 null。
 */
private fun resolveShareUri(context: Context, item: DownloadListItem): Uri? {
    val location = item.savePath ?: return null
    if (location.startsWith(CONTENT_SCHEME)) {
        return Uri.parse(location)
    }
    val file = File(location)
    if (!file.isFile) return null
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * 通过系统分享面板分享一个已下载文件。
 *
 * @param context 上下文。
 * @param item 列表条目。
 */
private fun shareDownload(context: Context, item: DownloadListItem) {
    val uri = resolveShareUri(context, item)
    if (uri == null) {
        Toast.makeText(context, R.string.download_share_unavailable, Toast.LENGTH_SHORT).show()
        return
    }
    val mimeType = context.contentResolver.getType(uri) ?: DEFAULT_MIME_TYPE
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(sendIntent, context.getString(R.string.download_share_chooser))
    )
}

/**
 * 调用系统安装器安装已下载的 APK。
 *
 * @param context 上下文。
 * @param item 列表条目。
 */
private fun installDownload(context: Context, item: DownloadListItem) {
    val uri = resolveShareUri(context, item)
    if (uri == null) {
        Toast.makeText(context, R.string.download_install_unavailable, Toast.LENGTH_SHORT).show()
        return
    }
    val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, APK_MIME_TYPE)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(installIntent)
    } catch (exception: ActivityNotFoundException) {
        Timber.e(exception, "DownloadScreen: no installer activity for %s", item.fileName)
        Toast.makeText(context, R.string.download_install_unavailable, Toast.LENGTH_SHORT).show()
    }
}

/** APK 文件后缀。 */
private const val APK_EXTENSION = ".apk"

/** APK MIME 类型。 */
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/** 内容 Uri 协议前缀。 */
private const val CONTENT_SCHEME = "content://"

/** 兜底 MIME 类型。 */
private const val DEFAULT_MIME_TYPE = "application/octet-stream"