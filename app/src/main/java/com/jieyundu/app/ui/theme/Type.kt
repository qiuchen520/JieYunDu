// 文件：Type.kt
// 职责：定义全局字体样式（9.3 文字层规格）
// 依赖：Compose Material3 Typography、Compose UI text
// 协议：AGPL-3.0

package com.jieyundu.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体样式表（对应《要求.md》9.3）。
 *
 * 说明：文字永远清晰、不参与模糊；字号以 sp 计。
 */
val JieYunDuTypography: Typography = Typography(
    /** 标题：28sp Bold。 */
    headlineLarge = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold
    ),
    /** 文件名 / 卡片标题：17sp SemiBold。 */
    titleMedium = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold
    ),
    /** 输入框正文：17sp Regular。 */
    bodyLarge = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.Normal
    ),
    /** 副标题 / 说明：15sp Regular。 */
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal
    ),
    /** 按钮文字：16sp Medium。 */
    labelLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
    ),
    /** 微型辅助文字：13sp Regular（9.6.4 速度文字）。 */
    labelMedium = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal
    ),
    /** 辅助文字：14sp Regular。 */
    labelSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal
    )
)
