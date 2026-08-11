package io.sanato.appkit.auth.net.hilt

import io.sanato.appkit.core.auth.AuthTokenProvider
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {
    private lateinit var server: MockWebServer

    private class FakeTokenProvider(
        var cached: String? = null,
    ) : AuthTokenProvider {
        override suspend fun currentIdToken(forceRefresh: Boolean): String? = cached

        override fun cachedIdToken(): String? = cached

        override suspend fun invalidateToken() {
            cached = null
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
    fun `attaches a Bearer header when a cached token is available`() {
        val tokenProvider = FakeTokenProvider(cached = "abc123")
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokenProvider)).build()
        server.enqueue(MockResponse(code = 200))

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        assertEquals("Bearer abc123", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `sends the request bare when there is no cached token`() {
        val tokenProvider = FakeTokenProvider(cached = null)
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokenProvider)).build()
        server.enqueue(MockResponse(code = 200))

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `does not overwrite a manually-set Authorization header`() {
        val tokenProvider = FakeTokenProvider(cached = "abc123")
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokenProvider)).build()
        server.enqueue(MockResponse(code = 200))

        client
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/"))
                    .header("Authorization", "Bearer manual-token")
                    .build(),
            ).execute()
            .close()

        assertEquals("Bearer manual-token", server.takeRequest().headers["Authorization"])
    }
}
