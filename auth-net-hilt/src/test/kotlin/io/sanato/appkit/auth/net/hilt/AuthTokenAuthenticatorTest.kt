package io.sanato.appkit.auth.net.hilt

import io.sanato.appkit.core.auth.AuthTokenProvider
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AuthTokenAuthenticatorTest {
    private lateinit var server: MockWebServer

    /**
     * `cachedIdToken()` (what [AuthInterceptor] reads for the *original*
     * request) only changes when [currentIdToken] is called with
     * `forceRefresh = true` — mirroring the real contract in
     * `:core-auth`'s `AuthTokenProvider` KDoc ("implementations must keep
     * this cache fresh"). [refreshedValue] is what a force-refresh resolves to.
     */
    private class FakeTokenProvider(
        initial: String?,
        var refreshedValue: String? = null,
    ) : AuthTokenProvider {
        var cached: String? = initial
        var invalidateCalls = 0
        var forceRefreshCalls = 0

        override suspend fun currentIdToken(forceRefresh: Boolean): String? {
            if (forceRefresh) {
                forceRefreshCalls++
                cached = refreshedValue
            }
            return cached
        }

        override fun cachedIdToken(): String? = cached

        override suspend fun invalidateToken() {
            invalidateCalls++
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
    fun `refreshes the token once and retries on a 401`() {
        val tokenProvider = FakeTokenProvider(initial = "stale", refreshedValue = "fresh")
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(AuthInterceptor(tokenProvider))
                .authenticator(AuthTokenAuthenticator(tokenProvider))
                .build()
        server.enqueue(MockResponse(code = 401))
        server.enqueue(MockResponse(code = 200, body = "ok"))

        val response = client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        assertEquals(200, response.code)
        assertEquals(1, tokenProvider.invalidateCalls)
        assertEquals(1, tokenProvider.forceRefreshCalls)
        server.takeRequest() // first attempt, "Bearer stale"
        assertEquals("Bearer fresh", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `does not retry a second time on consecutive 401s`() {
        val tokenProvider = FakeTokenProvider(initial = "v1", refreshedValue = "v2")
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(AuthInterceptor(tokenProvider))
                .authenticator(AuthTokenAuthenticator(tokenProvider))
                .build()
        repeat(2) { server.enqueue(MockResponse(code = 401)) }

        val response = client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        assertEquals(401, response.code)
        assertEquals(2, server.requestCount) // one retry with "v2", then give up — not an infinite loop.
        assertEquals(1, tokenProvider.forceRefreshCalls) // never asked for a third token.
    }

    @Test
    fun `gives up without retrying when no fresh token is available`() {
        val tokenProvider = FakeTokenProvider(initial = "stale", refreshedValue = null)
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(AuthInterceptor(tokenProvider))
                .authenticator(AuthTokenAuthenticator(tokenProvider))
                .build()
        server.enqueue(MockResponse(code = 401))

        val response = client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
    }
}
