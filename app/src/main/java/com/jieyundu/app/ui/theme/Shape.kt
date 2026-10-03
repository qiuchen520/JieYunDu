// 文件：Shape.kt
// 职责：定义全局形状（9.2 圆角规格）
// 依赖：Compose Material3 Shapes、Compose foundation shape
// 协议：AGPL-3.0

package com.jieyundu.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/**
 * 形状表（对应《要求.md》9.2）。
 *
 * 说明：圆角一律不小于 16dp；不使用全圆胶囊（导航条除外，见第十部分）。
 */
val JieYunDuShapes: Shapes = Shapes(
    /** 小元件圆角（下限 16dp）。 */
    small = RoundedCornerShape(Dimens.MinCorner),
    /** 玻璃面板 / 卡片圆角。 */
    medium = RoundedCornerShape(Dimens.CardCorner),
    /** 大面板圆角。 */
    large = RoundedCornerShape(Dimens.CardCorner)
)
