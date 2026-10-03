// 文件：MainActivity.kt
// 职责：应用入口 Activity（阶段 7 接线：浅色背板 + Q 弹玻璃导航条）
// 依赖：JieYunDuTheme、GlassBackground、JieYunDuNavHost、Hilt
// 协议：AGPL-3.0

package com.jieyundu.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jieyundu.app.ui.glass.GlassBackground
import com.jieyundu.app.ui.navigation.JieYunDuNavHost
import com.jieyundu.app.ui.theme.JieYunDuTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 应用唯一 Activity。
 *
 * 说明：装载浅色背板与导航宿主。平板显示左侧竖直玻璃条，手机显示顶部横向玻璃条；
 * 三个页签的真实内容（首页 / 下载 / 设置）由 [JieYunDuNavHost] 在阶段 8 接入。
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
                    JieYunDuNavHost()
                }
            }
        }
    }
}