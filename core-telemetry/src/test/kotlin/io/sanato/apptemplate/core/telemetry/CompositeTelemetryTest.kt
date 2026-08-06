package io.sanato.apptemplate.core.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeTelemetryTest {
    private class RecordingTelemetry : Telemetry {
        override val isEnabled = true
        var lastEvent: String? = null

        override fun event(
            name: String,
            params: Map<String, Any?>,
        ) {
            lastEvent = name
        }

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

    private class ThrowingTelemetry : Telemetry by NoOpTelemetry {
        override fun event(
            name: String,
            params: Map<String, Any?>,
        ): Unit = throw IllegalStateException("boom")
    }

    @Test
    fun `a failing backend does not stop other backends from receiving the event`() {
        val healthy = RecordingTelemetry()
        val composite = CompositeTelemetry(setOf(ThrowingTelemetry(), healthy))

        composite.event("test_event")

        assertEquals("test_event", healthy.lastEvent)
    }

    @Test
    fun `isEnabled is true when at least one backend is enabled`() {
        val composite = CompositeTelemetry(setOf(NoOpTelemetry, RecordingTelemetry()))
        assertTrue(composite.isEnabled)
    }
}
