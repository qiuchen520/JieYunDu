// 文件：DownloadGlyphs.kt
// 职责：下载页行尾三个按钮的自绘图标（分享 / 下载到托盘 / 垃圾桶）
// 依赖：Compose Canvas、Offset、StrokeCap、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.icons

// 【修订 JYD-DEBT7-2026-10-07】自 DownloadItem.kt 拆出：三个图标与 25 个几何常量原与
// 下载项业务组件混在一个文件（约 250 行）。图标是纯绘制、不含任何业务状态，独立后
// DownloadItem.kt 只剩「一条下载项长什么样」。可见性 internal（仅模块内使用）。

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 自绘「分享」图标（三个节点 + 两条连线）。
 *
 * @param modifier 外部修饰符。
 */
@Composable
internal fun ShareGlyph(modifier: Modifier = Modifier) {
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
internal fun InstallGlyph(modifier: Modifier = Modifier) {
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
internal fun TrashGlyph(modifier: Modifier = Modifier) {
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

