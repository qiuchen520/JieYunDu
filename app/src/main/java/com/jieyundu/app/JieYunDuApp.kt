// 文件：JieYunDuApp.kt
// 职责：Application 入口，初始化 Hilt 依赖注入容器与 Timber 日志
// 依赖：Timber、Hilt（dagger.hilt.android.HiltAndroidApp）
// 协议：AGPL-3.0

package com.jieyundu.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * 极云渡 Application 入口。
 *
 * 职责：
 * 1. 通过 [HiltAndroidApp] 生成依赖注入容器；
 * 2. 在 Debug 构建下挂载 Timber 的 DebugTree（C8：日志统一走 Timber）。
 *
 * 注意：此处不注册任何全局静态变量，避免多进程/热重载场景下的状态污染。
 */
@HiltAndroidApp
class JieYunDuApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
