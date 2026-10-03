// 文件：CrashReporter.kt
// 职责：全局未捕获异常捕获与崩溃日志落盘 / 查询 / 清理 / 导出准备（仅本地，不上传）
// 依赖：Context、BuildConfig、Timber、java.io
// 协议：AGPL-3.0

package com.jieyundu.app.util

import android.content.Context
import android.os.Build
import com.jieyundu.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import timber.log.Timber

/**
 * 崩溃日志记录器（阶段 11 修复追加：让闪退可被记录与导出）。
 *
 * 行为：
 * 1. [install] 注册 [Thread.setDefaultUncaughtExceptionHandler]，崩溃时先落盘再交给原处理器，
 *    保证系统仍按正常流程崩溃（不吞异常、不改变崩溃行为）；
 * 2. 日志写入 App 私有目录 `files/crash_logs/crash_yyyyMMdd_HHmmss.txt`；
 * 3. 最多保留最近 [MAX_LOGS] 条，超出后删除最旧的；
 * 4. [prepareShareFile] 把最新一条复制到 `cache/crash_logs/` 供 FileProvider 共享。
 *
 * 隐私：日志仅存本地，App 不上传。日志内容含崩溃前最近 [MAX_RECENT_LINES] 行 Timber 输出。
 *
 * 线程约束：可在任意线程调用；内部用锁保护最近日志环形缓冲。
 */
object CrashReporter {

    /** 崩溃日志目录名（位于 [Context.getFilesDir] 之下）。 */
    private const val DIR_NAME = "crash_logs"

    /** 最多保留的崩溃日志条数。 */
    private const val MAX_LOGS = 10

    /** 环形缓冲保留的最近日志行数（崩溃时一并写入）。 */
    private const val MAX_RECENT_LINES = 100

    /** 落盘文件名前缀。 */
    private const val FILE_PREFIX = "crash_"

    /** 导出（分享）文件名前缀。 */
    private const val SHARE_PREFIX = "jieyundu_crash_"

    /** 文件名时间戳格式。 */
    private const val TIME_PATTERN = "yyyyMMdd_HHmmss"

    /** 崩溃前最近日志行（环形缓冲）。 */
    private val recentLines: ArrayDeque<String> = ArrayDeque()

    /** 保护 [recentLines] 的锁。 */
    private val recentLock = Any()

    /**
     * 注册全局未捕获异常处理器。
     *
     * 说明：应在 [android.app.Application.onCreate] 中尽早调用；重复调用会以上一次处理器
     * 作为链式下游，因此只应调用一次。
     *
     * @param context 应用上下文（内部取 applicationContext，不持有 Activity）。
     */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(appContext, thread, throwable)
            } catch (ignored: Throwable) {
                // 写日志本身失败绝不能再抛，否则会掩盖原始崩溃。
                Timber.e(ignored, "CrashReporter failed to persist crash log")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 记录一行日志到环形缓冲（由 [RecentLogTree] 调用）。
     *
     * @param line 已格式化的一行日志。
     */
    fun record(line: String) {
        synchronized(recentLock) {
            recentLines.addLast(line)
            while (recentLines.size > MAX_RECENT_LINES) {
                recentLines.removeFirst()
            }
        }
    }

    /**
     * 崩溃日志目录（可能尚不存在）。
     *
     * @param context 应用上下文。
     * @return 目录 File。
     */
    fun directory(context: Context): File = File(context.filesDir, DIR_NAME)

    /**
     * 列出全部崩溃日志，按文件名倒序（最新在前）。
     *
     * @param context 应用上下文。
     * @return 日志文件列表；无日志时为空列表。
     */
    fun listLogs(context: Context): List<File> =
        directory(context)
            .listFiles { file -> file.isFile && file.name.startsWith(FILE_PREFIX) }
            ?.sortedByDescending { file -> file.name }
            .orEmpty()

    /**
     * 是否存在崩溃日志。
     *
     * @param context 应用上下文。
     * @return true 表示至少有一条日志。
     */
    fun hasLogs(context: Context): Boolean = listLogs(context).isNotEmpty()

    /**
     * 清空全部崩溃日志。
     *
     * @param context 应用上下文。
     * @return 实际删除的文件数量。
     */
    fun clear(context: Context): Int {
        var deleted = 0
        listLogs(context).forEach { file ->
            if (file.delete()) {
                deleted++
            }
        }
        return deleted
    }

    /**
     * 把最新一条崩溃日志复制到缓存目录，供 FileProvider 共享。
     *
     * 说明：复制而非直接共享 files 目录文件，避免额外扩大 FileProvider 暴露范围；
     * 目标文件名为 `jieyundu_crash_yyyyMMdd_HHmmss.txt`（与需求一致）。
     *
     * @param context 应用上下文。
     * @return 可共享的缓存文件；无日志时为 null。
     */
    fun prepareShareFile(context: Context): File? {
        val latest = listLogs(context).firstOrNull() ?: return null
        val shareDir = File(context.cacheDir, DIR_NAME)
        if (!shareDir.exists() && !shareDir.mkdirs()) {
            Timber.e("CrashReporter failed to create share dir: %s", shareDir.absolutePath)
            return null
        }
        val stamp = currentStamp()
        val target = File(shareDir, "$SHARE_PREFIX$stamp.txt")
        latest.copyTo(target, overwrite = true)
        return target
    }

    /**
     * 写入一条崩溃日志，并按保留策略裁剪旧日志。
     *
     * @param context 应用上下文。
     * @param thread 崩溃线程。
     * @param throwable 崩溃异常。
     */
    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val dir = directory(context)
        if (!dir.exists() && !dir.mkdirs()) {
            Timber.e("CrashReporter failed to create crash dir: %s", dir.absolutePath)
            return
        }
        val target = File(dir, "$FILE_PREFIX${currentStamp()}.txt")
        target.bufferedWriter().use { writer ->
            writer.appendLine("===== 极云渡 崩溃日志 =====")
            writer.appendLine("时间：" + formatTime(System.currentTimeMillis()))
            writer.appendLine("版本：" + BuildConfig.VERSION_NAME)
            writer.appendLine("Android：" + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
            writer.appendLine("设备：" + Build.MANUFACTURER + " " + Build.MODEL)
            writer.appendLine("线程：" + thread.name)
            writer.appendLine()
            writer.appendLine("----- 异常堆栈 -----")
            writer.appendLine(stackTraceOf(throwable))
            writer.appendLine()
            writer.appendLine("----- 崩溃前最近 $MAX_RECENT_LINES 行日志 -----")
            writer.appendLine(recentSnapshot())
        }
        pruneOldLogs(context)
    }

    /**
     * 按 [MAX_LOGS] 裁剪旧日志（保留文件名倒序的前 N 条）。
     *
     * @param context 应用上下文。
     */
    private fun pruneOldLogs(context: Context) {
        val logs = listLogs(context)
        if (logs.size <= MAX_LOGS) {
            return
        }
        logs.drop(MAX_LOGS).forEach { file -> file.delete() }
    }

    /**
     * 取最近日志的快照文本。
     *
     * @return 多行文本；缓冲为空时返回占位说明。
     */
    private fun recentSnapshot(): String {
        val snapshot = synchronized(recentLock) { recentLines.toList() }
        return if (snapshot.isEmpty()) {
            "（无可用日志缓冲）"
        } else {
            snapshot.joinToString(separator = "\n")
        }
    }

    /**
     * 把异常转为完整堆栈字符串。
     *
     * @param throwable 崩溃异常。
     * @return 堆栈文本。
     */
    private fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { printer -> throwable.printStackTrace(printer) }
        return writer.toString()
    }

    /**
     * 当前时间戳（文件名用）。
     *
     * @return 形如 `20261003_123456`。
     */
    private fun currentStamp(): String =
        SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date())

    /**
     * 可读时间戳（日志内容用）。
     *
     * @param millis 毫秒时间戳。
     * @return 形如 `2026-10-03 12:34:56`。
     */
    private fun formatTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
}
