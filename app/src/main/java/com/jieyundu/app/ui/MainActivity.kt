// 文件：MainActivity.kt
// 职责：应用入口 Activity（阶段 6 最小壳：深色背板 + 一块玻璃 + 占位文字）
// 依赖：JieYunDuTheme、GlassBackground、GlassCard、Hilt
// 协议：AGPL-3.0

package com.jieyundu.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jieyundu.app.R
import com.jieyundu.app.ui.glass.GlassBackground
import com.jieyundu.app.ui.glass.GlassCard
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors
import com.jieyundu.app.ui.theme.JieYunDuTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 应用唯一 Activity（阶段 6 交付最小壳）。
 *
 * 说明：完整界面（导航 + 三个页面）在阶段 7 / 8 落地；此处仅用于验证
 * 玻璃组件可编译、可显示，以及 APK 可安装可打开。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * 创建 Activity 并装载 Compose 内容。
     *
     * @param savedInstanceState 保存的实例状态。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JieYunDuTheme {
                GlassBackground {
                    MainPlaceholder()
                }
            }
        }
    }
}

/**
 * 主界面占位内容。
 */
@Composable
private fun MainPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SpaceXl),
        contentAlignment = Alignment.Center
    ) {
        GlassCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = JieYunDuColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(Dimens.SpaceSm))
                Text(
                    text = stringResource(R.string.home_glass_sample),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JieYunDuColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(Dimens.SpaceXs))
                Text(
                    text = stringResource(R.string.home_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JieYunDuColors.TextSecondary
                )
            }
        }
    }
}
