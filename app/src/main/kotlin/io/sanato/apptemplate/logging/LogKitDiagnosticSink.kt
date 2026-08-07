package io.sanato.apptemplate.logging

import io.sanato.appkit.core.telemetry.DiagnosticLevel
import io.sanato.appkit.core.telemetry.DiagnosticLogSink
import io.sanato.logkit.LogKit

/**
 * 两个调用点共用同一份桥接实现:
 *  1. `AppTemplateApp.attachBaseContext` —— Hilt 还不存在,手动传给
 *     `CrashRecorder.install(context, LogKitDiagnosticSink)`。
 *  2. [LogKitModule] 的 `@Provides` —— 给 `AnrCheckInitializer` 之类 DI 注入方。
 *
 * 不做成 `@Provides` 独占,是因为(1)拿不到 Hilt 图;不做成可变 static var,
 * 是因为那样就变成可变全局状态了(ADR-0006 明确否决反射/隐式后端选择的
 * 同一个理由:接线错误应该是编译错误,不是运行时静默 no-op)。`object` 是
 * 唯一同时满足两者的形态。
 */
object LogKitDiagnosticSink : DiagnosticLogSink {
    override fun log(
        level: DiagnosticLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        when (level) {
            // FATAL 的语义由本桥接方定义(:core-telemetry 不知道"落盘"这件事
            // 存在):写入后立即同步落盘,是崩溃瞬间持久化承诺的落点。
            DiagnosticLevel.FATAL -> {
                LogKit.fatal(tag, message, throwable)
            }

            DiagnosticLevel.ERROR -> {
                if (throwable !=
                    null
                ) {
                    LogKit.e(tag, message, throwable)
                } else {
                    LogKit.e(tag, message)
                }
            }

            DiagnosticLevel.WARN -> {
                LogKit.w(tag, message)
            }

            DiagnosticLevel.INFO -> {
                LogKit.i(tag, message)
            }

            DiagnosticLevel.DEBUG -> {
                LogKit.d(tag, message)
            }
        }
    }
}
