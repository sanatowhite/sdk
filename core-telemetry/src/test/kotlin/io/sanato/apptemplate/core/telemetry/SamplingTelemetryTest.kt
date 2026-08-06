package io.sanato.apptemplate.core.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplingTelemetryTest {
    private class RecordingTelemetry : Telemetry {
        override val isEnabled = true
        var eventCount = 0
        var crashCount = 0

        override fun event(
            name: String,
            params: Map<String, Any?>,
        ) {
            eventCount++
        }

        override fun screenView(screenName: String) = Unit

        override fun startup(report: StartupReport) = Unit

        override fun frame(report: FrameReport) = Unit

        override fun networkRequest(report: NetworkRequestReport) = Unit

        override fun crash(
            throwable: Throwable,
            fatal: Boolean,
        ) {
            crashCount++
        }

        override fun anr(report: AnrReport) = Unit
    }

    @Test
    fun `session sampled out forwards nothing but crashes`() {
        val delegate = RecordingTelemetry()
        // sampleRate=0.5, randomValue=0.9 -> 0.9 !< 0.5 -> sampled OUT this session
        val sampling = SamplingTelemetry(delegate, sampleRate = 0.5f, randomValue = 0.9f)

        sampling.event("a")
        sampling.event("b")
        sampling.crash(IllegalStateException(), fatal = true)

        assertFalse(sampling.isEnabled)
        assertEquals(0, delegate.eventCount)
        assertEquals(1, delegate.crashCount)
    }

    @Test
    fun `session sampled in forwards everything`() {
        val delegate = RecordingTelemetry()
        // sampleRate=0.5, randomValue=0.1 -> 0.1 < 0.5 -> sampled IN this session
        val sampling = SamplingTelemetry(delegate, sampleRate = 0.5f, randomValue = 0.1f)

        sampling.event("a")
        sampling.event("b")

        assertTrue(sampling.isEnabled)
        assertEquals(2, delegate.eventCount)
    }
}
