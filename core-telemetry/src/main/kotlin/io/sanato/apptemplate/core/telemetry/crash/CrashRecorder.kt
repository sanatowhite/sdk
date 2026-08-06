package io.sanato.apptemplate.core.telemetry.crash

import android.content.Context
import io.sanato.apptemplate.core.telemetry.DiagnosticLevel
import io.sanato.apptemplate.core.telemetry.DiagnosticLogSink
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
 *
 * [logSink] 同理:本类【不】自己调用任何 `Telemetry` 后端,只把 FATAL 级别的
 * 一条诊断日志转发给 `:app` 在 `attachBaseContext` 里手动传进来的桥接实现
 * (那时候 Hilt 还不存在,拿不到任何 DI 注入的东西,调用点是手写的,所以
 * 用默认参数而不是可变 static——见 docs/adr/0008)。默认 `DiagnosticLogSink.NoOp`
 * 让本类在没有 `:logkit`/`:app` 桥接的场景下(单独复用 `:core-telemetry`)
 * 保持零成本、行为不变。
 */
class CrashRecorder private constructor(
    private val crashDir: File,
    private val previousHandler: Thread.UncaughtExceptionHandler?,
    private val logSink: DiagnosticLogSink,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        // 顺序:先写现有的明文 marker(最便宜、最可靠,下次启动读)——再转发给
        // FATAL 日志(会阻塞在同步 flush 上)——最后才委派给 previousHandler
        // (可能是 Crashlytics)。`runCatching` 包住 logSink 调用是强制的:一个
        // 抛异常的 sink 绝不能妨碍 previousHandler 运行。
        runCatching { writeCrashMarker(thread, throwable) }
        runCatching { logSink.log(DiagnosticLevel.FATAL, TAG, "uncaught exception on ${thread.name}", throwable) }
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
        private const val TAG = "CrashRecorder"
        private const val CRASH_DIR_NAME = "telemetry_crash_reports"

        /** 链式安装:把当前默认 handler 存成 previous,再把自己设成新的默认 handler。 */
        fun install(
            context: Context,
            logSink: DiagnosticLogSink = DiagnosticLogSink.NoOp,
        ): CrashRecorder {
            val recorder =
                CrashRecorder(
                    crashDir = crashDir(context),
                    previousHandler = Thread.getDefaultUncaughtExceptionHandler(),
                    logSink = logSink,
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
