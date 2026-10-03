// 文件：JieYunDuApp.kt
// 职责：Application 入口，初始化 Hilt 依赖注入容器、Timber 日志与崩溃捕获
// 依赖：Timber、Hilt（dagger.hilt.android.HiltAndroidApp）、CrashReporter、RecentLogTree
// 协议：AGPL-3.0

package com.jieyundu.app

import android.app.Application
import com.jieyundu.app.util.CrashReporter
import com.jieyundu.app.util.RecentLogTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * 极云渡 Application 入口。
 *
 * 职责：
 * 1. 通过 [HiltAndroidApp] 生成依赖注入容器；
 * 2. 注册全局崩溃捕获（[CrashReporter.install]），让闪退可被记录与导出（修复追加）；
 * 3. 挂载 [RecentLogTree] 收集崩溃前最近的 Timber 日志；
 * 4. 在 Debug 构建下额外挂载 [Timber.DebugTree]（C8：日志统一走 Timber）。
 *
 * 注意：此处不注册任何全局静态变量，避免多进程/热重载场景下的状态污染。
 */
@HiltAndroidApp
class JieYunDuApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 崩溃捕获需尽早注册：先装日志树，再注册异常处理器，保证崩溃上下文尽量完整。
        Timber.plant(RecentLogTree())
        CrashReporter.install(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
