package io.sanato.appkit.core.telemetry.crash

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 装的时机必须在 `attachBaseContext`(比 Hilt 组装更早),让 Crashlytics 之类
 * 第三方 handler 能挂在外层(链式调用 previous)。
 *
 * 绝不在 handler 里调 Firebase SDK 或任何需要 DI/网络的上报逻辑——会 fatal +
 * non-fatal 重复上报,而且崩溃现场的进程状态本就不可靠。handler 内只做同步
 * 的小文件写,下次启动时才通过 [drainPendingCrashReports] 真正上报。
 */
class CrashRecorder private constructor(
    private val crashDir: File,
    private val previousHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        runCatching { writeCrashMarker(thread, throwable) }
        previousHandler?.uncaughtException(thread, throwable)
    }

    private fun writeCrashMarker(
        thread: Thread,
        throwable: Throwable,
    ) {
        if (!crashDir.exists()) crashDir.mkdirs()
        val file = File(crashDir, "crash_${System.currentTimeMillis()}.txt")
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        file.writeText("thread=${thread.name}\n$stackTrace")
    }

    companion object {
        private const val CRASH_DIR_NAME = "telemetry_crash_reports"

        /** 链式安装:把当前默认 handler 存成 previous,再把自己设成新的默认 handler。 */
        fun install(context: Context): CrashRecorder {
            val recorder =
                CrashRecorder(
                    crashDir = crashDir(context),
                    previousHandler = Thread.getDefaultUncaughtExceptionHandler(),
                )
            Thread.setDefaultUncaughtExceptionHandler(recorder)
            return recorder
        }

        /** 下次启动读取并清空上次崩溃留下的文件,交给 Telemetry 上报;找不到文件时返回空表。 */
        fun drainPendingCrashReports(context: Context): List<String> {
            val dir = crashDir(context)
            if (!dir.exists()) return emptyList()
            val files = dir.listFiles().orEmpty()
            val contents = files.mapNotNull { runCatching { it.readText() }.getOrNull() }
            files.forEach { it.delete() }
            return contents
        }

        private fun crashDir(context: Context): File = File(context.filesDir, CRASH_DIR_NAME)
    }
}
