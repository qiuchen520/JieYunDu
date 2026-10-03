// 文件：RecentLogTree.kt
// 职责：把 Timber 日志同步进 CrashReporter 的最近日志环形缓冲，供崩溃日志附带上下文
// 依赖：Timber、CrashReporter
// 协议：AGPL-3.0

package com.jieyundu.app.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import timber.log.Timber

/**
 * 最近日志收集树。
 *
 * 说明（崩溃日志需求 3.2「崩溃前最后 100 行 Timber 日志」）：本树把每条日志格式化为
 * 单行文本后交给 [CrashReporter.record]，缓冲长度上限由 CrashReporter 控制。
 *
 * 与 [Timber.DebugTree] 不冲突：DebugTree 负责打印到 Logcat，本树只做内存留存，
 * 因此在 Debug 与 Release 下都会挂载（Release 不留日志到 Logcat，但仍保留崩溃上下文）。
 */
class RecentLogTree : Timber.Tree() {

    /** 单行日志时间戳格式。 */
    private val timeFormat = SimpleDateFormat(TIME_PATTERN, Locale.US)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val line = buildString {
            append(timeFormat.format(Date()))
            append(' ')
            append(priorityLabel(priority))
            append('/')
            append(tag ?: DEFAULT_TAG)
            append(": ")
            append(message)
            if (t != null) {
                append(" | ")
                append(t.javaClass.simpleName)
                append(": ")
                append(t.message ?: "")
            }
        }
        CrashReporter.record(line)
    }

    /**
     * 把优先级映射为单字母标签。
     *
     * @param priority [Log] 优先级。
     * @return 单字母标签。
     */
    private fun priorityLabel(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> "?"
    }

    private companion object {
        /** 日志行时间戳格式。 */
        const val TIME_PATTERN = "HH:mm:ss.SSS"

        /** 无 tag 时的占位。 */
        const val DEFAULT_TAG = "-"
    }
}