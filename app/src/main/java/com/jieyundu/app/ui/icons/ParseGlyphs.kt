// 文件：ParseGlyphs.kt
// 职责：首页解析结果里的两个自绘图标（文件夹 / 勾选对勾）
// 依赖：Compose Canvas、Offset、Size/CornerRadius、StrokeCap、JieYunDuColors
// 协议：AGPL-3.0

package com.jieyundu.app.ui.icons

// 【修订 JYD-DEBT11-2026-10-07】自 ParseResultCard.kt 拆出：两个图标与 15 个几何常量
// 原是解析结果卡片的一部分（卡片 590 行）。图标是纯绘制、不含业务状态，独立成文件后
// ParseResultCard 只描述「解析结果长什么样」。可见性 internal（仅模块内使用）。

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import com.jieyundu.app.ui.theme.JieYunDuColors

/**
 * 自绘「文件夹」图标（D8：不引入 material-icons）。
 *
 * @param modifier 外部修饰符。
 */
@Composable
internal fun FolderGlyph(modifier: Modifier = Modifier) {
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
internal fun CheckGlyph(modifier: Modifier = Modifier) {
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

