package io.sanato.apptemplate.logging

import io.sanato.apptemplate.core.telemetry.AnrReport
import io.sanato.apptemplate.core.telemetry.FrameReport
import io.sanato.apptemplate.core.telemetry.NetworkRequestReport
import io.sanato.apptemplate.core.telemetry.StartupReport
import io.sanato.apptemplate.core.telemetry.Telemetry
import io.sanato.logkit.LogKit

/**
 * 把 `Telemetry` 绑成 `@IntoSet` 后端(见 [LogKitModule])—— `RingLogBuffer` 已经
 * 证明了这条路可以免费收到全部 `startup/frame/networkRequest/crash/anr/
 * screenView/event` 信号,不需要在每个 collector 里额外穿一个 sink 参数。
 * `:core-telemetry` 因此零改动——这正是两通道设计(见 `DiagnosticLogSink`)
 * 把"结构上能覆盖的信号"和"崩溃处理器/ANR trace 这两个例外"分开的意义。
 */
class LogKitTelemetry : Telemetry {
    // 本期不做同意门控(用户决定,见 docs/adr/0008)——LogKit 是否落盘完全由
    // LogKit.install() 决定,这里恒真,不额外接 UserSettings.telemetryEnabled。
    override val isEnabled: Boolean = true

    override fun event(
        name: String,
        params: Map<String, Any?>,
    ) {
        LogKit.d("Telemetry", "event: $name $params")
    }

    override fun screenView(screenName: String) {
        LogKit.i("Telemetry", "screenView: $screenName")
    }

    override fun startup(report: StartupReport) {
        LogKit.i(
            "Telemetry",
            "startup: launchType=${report.launchType} ttid=${report.timeToInitialDisplayMillis}ms ttfd=${report.timeToFullDisplayMillis}ms",
        )
    }

    override fun frame(report: FrameReport) {
        val message =
            "frame: screen=${report.screenName} total=${report.totalFrames} " +
                "janky=${report.jankyFrames} ratio=${report.jankyFrameRatio}"
        if (report.jankyFrameRatio > JANKY_RATIO_WARN_THRESHOLD) {
            LogKit.w("Telemetry", message)
        } else {
            LogKit.d("Telemetry", message)
        }
    }

    override fun networkRequest(report: NetworkRequestReport) {
        val message =
            "networkRequest: ${report.method} ${report.routeTemplate} status=${report.httpStatus} " +
                "totalMillis=${report.totalMillis} failed=${report.failed}"
        val httpStatus = report.httpStatus
        val isWarn = report.failed || (httpStatus != null && httpStatus >= HTTP_ERROR_STATUS_THRESHOLD)
        if (isWarn) LogKit.w("Telemetry", message) else LogKit.i("Telemetry", message)
    }

    override fun crash(
        throwable: Throwable,
        fatal: Boolean,
    ) {
        LogKit.e("Telemetry", "crash: fatal=$fatal ${throwable.javaClass.name}: ${throwable.message}", throwable)
    }

    override fun anr(report: AnrReport) {
        LogKit.w("Telemetry", "anr: source=${report.source}")
    }

    private companion object {
        const val JANKY_RATIO_WARN_THRESHOLD = 0.1f
        const val HTTP_ERROR_STATUS_THRESHOLD = 400
    }
}
