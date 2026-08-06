package io.sanato.apptemplate.core.net

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TelemetryEventListenerFactoryTest {
    private lateinit var server: MockWebServer

    private class RecordingSink : NetworkMetricsSink {
        var lastMethod: String? = null
        var lastStatus: Int? = null
        var lastFailed: Boolean? = null
        var reportCount = 0

        override fun onRequestCompleted(
            routeTemplate: String,
            method: String,
            httpStatus: Int?,
            totalMillis: Long,
            failed: Boolean,
        ) {
            reportCount++
            lastMethod = method
            lastStatus = httpStatus
            lastFailed = failed
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.close() }
    }

    @Test
    fun `reports method status and success on a completed call`() {
        val sink = RecordingSink()
        val client =
            OkHttpClient
                .Builder()
                .eventListenerFactory(TelemetryEventListenerFactory(sink))
                .build()
        server.enqueue(MockResponse(code = 200, body = "ok"))

        client.newCall(Request.Builder().url(server.url("/ping")).build()).execute().close()

        assertEquals(1, sink.reportCount)
        assertEquals("GET", sink.lastMethod)
        assertEquals(200, sink.lastStatus)
        assertFalse(sink.lastFailed!!)
    }

    @Test
    fun `reports failure when the server is unreachable`() {
        val sink = RecordingSink()
        val client =
            OkHttpClient
                .Builder()
                .eventListenerFactory(TelemetryEventListenerFactory(sink))
                .build()
        server.close()

        runCatching {
            client.newCall(Request.Builder().url(server.url("/ping")).build()).execute()
        }

        assertEquals(1, sink.reportCount)
        assertTrue(sink.lastFailed!!)
    }
}
