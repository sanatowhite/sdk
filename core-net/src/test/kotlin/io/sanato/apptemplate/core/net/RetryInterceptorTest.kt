package io.sanato.apptemplate.core.net

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RetryInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            OkHttpClient
                .Builder()
                .addInterceptor(RetryInterceptor(maxRetries = 3, baseDelayMillis = 1, maxDelayMillis = 5))
                .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `retries on 503 and eventually succeeds`() {
        server.enqueue(MockResponse(code = 503))
        server.enqueue(MockResponse(code = 503))
        server.enqueue(MockResponse(code = 200, body = "ok"))

        val response = client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        assertEquals(200, response.code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `does not retry POST even on 503`() {
        server.enqueue(MockResponse(code = 503))

        val body = "payload".toRequestBody("text/plain".toMediaType())
        val response =
            client
                .newCall(
                    Request
                        .Builder()
                        .url(server.url("/"))
                        .post(body)
                        .build(),
                ).execute()

        assertEquals(503, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `honors Retry-After header on 429`() {
        server.enqueue(MockResponse(code = 429, headers = headersOf("Retry-After", "0")))
        server.enqueue(MockResponse(code = 200, body = "ok"))

        val response = client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `gives up after maxRetries and returns last failing response`() {
        repeat(4) { server.enqueue(MockResponse(code = 503)) }

        val response = client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        assertEquals(503, response.code)
        assertEquals(4, server.requestCount)
    }
}
