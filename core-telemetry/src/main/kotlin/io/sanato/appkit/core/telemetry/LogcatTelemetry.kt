package io.sanato.appkit.core.telemetry

import android.util.Log

class LogcatTelemetry(
    private val tag: String = "Telemetry",
) : Telemetry {
    override val isEnabled = true

    override fun event(
        name: String,
        params: Map<String, Any?>,
    ) {
        Log.d(tag, "event: $name $params")
    }

    override fun screenView(screenName: String) {
        Log.d(tag, "screenView: $screenName")
    }

    override fun startup(report: StartupReport) {
        Log.d(tag, "startup: $report")
    }

    override fun frame(report: FrameReport) {
        Log.d(tag, "frame: $report")
    }

    override fun networkRequest(report: NetworkRequestReport) {
        Log.d(tag, "networkRequest: $report")
    }

    override fun crash(
        throwable: Throwable,
        fatal: Boolean,
    ) {
        Log.e(tag, "crash(fatal=$fatal)", throwable)
    }

    override fun anr(report: AnrReport) {
        Log.w(tag, "anr: $report")
    }
}
