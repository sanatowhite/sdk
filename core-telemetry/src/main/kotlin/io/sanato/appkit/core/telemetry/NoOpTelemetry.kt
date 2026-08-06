package io.sanato.appkit.core.telemetry

object NoOpTelemetry : Telemetry {
    override val isEnabled = false

    override fun event(
        name: String,
        params: Map<String, Any?>,
    ) = Unit

    override fun screenView(screenName: String) = Unit

    override fun startup(report: StartupReport) = Unit

    override fun frame(report: FrameReport) = Unit

    override fun networkRequest(report: NetworkRequestReport) = Unit

    override fun crash(
        throwable: Throwable,
        fatal: Boolean,
    ) = Unit

    override fun anr(report: AnrReport) = Unit
}
