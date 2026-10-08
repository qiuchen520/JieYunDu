// 文件：SettingsCard.kt
// 职责：设置页通用积木——信息卡容器 + 档位选择行（四张档位卡共用）
// 依赖：GlassCard、GlassChip、Dimens、JieYunDuColors、strings.xml
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.jieyundu.app.ui.components.GlassChip
import com.jieyundu.app.ui.glass.GlassCard
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors

/** 【修订 JYD-DEBT7-2026-10-07】自 SettingsScreen.kt 拆出：卡片容器，供全部设置卡复用。 */
/**
 * 设置页信息卡：标题 + 若干正文行。
 *
 * @param title 卡片标题。
 * @param contentPadding 卡内边距。
 * @param content 正文内容。
 */
@Composable
internal fun SettingsCard(
    title: String,
    contentPadding: Dp,
    content: @Composable () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Dimens.CardCorner,
        contentPadding = contentPadding
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = JieYunDuColors.TextPrimary
            )
            content()
        }
    }
}

/**
 * 档位选择行：标题 + 说明 + 一排玻璃胶囊（【修订 JYD-DEBT7-2026-10-07】去重）。
 *
 * 存在理由：分片数 / 同时任务数 / 限速 / 重试次数四张卡的内部结构逐字相同，仅
 * 标题、说明、选项与回调不同；本组件把该结构收敛为**单一实现**（原先 4 处各约 25 行）。
 *
 * @param titleRes 卡片标题资源。
 * @param hint 说明文案（调用方已解析，含格式参数的情况在此之外完成）。
 * @param options 可选项（泛型，容纳 Int 与 Long 两类档位）。
 * @param selected 当前选中项。
 * @param labelFor 选项 → 展示文案（可能来自 strings.xml，故为 @Composable）。
 * @param onSelect 选中回调。
 * @param contentPadding 卡内边距。
 */
@Composable
internal fun <T> SettingsOptionRow(
    titleRes: Int,
    hint: String,
    options: List<T>,
    selected: T,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    contentPadding: Dp
) {
    SettingsCard(title = stringResource(titleRes), contentPadding = contentPadding) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = JieYunDuColors.TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
        ) {
            options.forEach { option ->
                GlassChip(
                    label = labelFor(option),
                    selected = option == selected,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}
