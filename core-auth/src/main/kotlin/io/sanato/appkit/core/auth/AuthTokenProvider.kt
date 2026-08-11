package io.sanato.appkit.core.auth

/**
 * Token access for the network layer (HTTP/WebSocket). Defined here, not in
 * `:core-net`, even though the `NetworkMetricsSink`-in-`:core-net` precedent
 * might suggest otherwise: that precedent exists because `:core-net` needs an
 * outlet it can call without depending on telemetry. That reasoning doesn't
 * transfer here — `:core-net`'s `HttpClientFactory.okHttpClient()` signature
 * is frozen by `apiCheck` and has nowhere to plug this in; the only consumer
 * would be a Tier-3 bridge module (`:auth-net-hilt`), so putting it in
 * `:core-net` would just be a dead declaration nobody calls (this repo just
 * removed one exactly like that — see `:core-data`'s build file history).
 * `AuthTokenProvider` is also useful to non-HTTP consumers (gRPC, Firestore
 * rules, a WebSocket handshake) — it's part of "owning an identity," not part
 * of "owning a network stack."
 */
interface AuthTokenProvider {
    /**
     * Suspends to fetch a token. [forceRefresh] = false should prefer a
     * locally cached, unexpired token over a network call. Returns `null`
     * when signed out — that's not an error condition.
     */
    suspend fun currentIdToken(forceRefresh: Boolean = false): String?

    /**
     * ⚠️ Non-suspending on purpose: OkHttp's `Interceptor.intercept()` isn't
     * `suspend`, and `runBlocking` on every request is unacceptable (see
     * `:auth-net-hilt`'s README for the full tradeoff). Contract: read-only
     * from a local cache, never blocks, never makes a network call; returns
     * `null` when there's no cached value, leaving it to the caller whether
     * to send the request bare or give up. Implementations must keep this
     * cache fresh on their own (e.g. a token-refresh listener).
     */
    fun cachedIdToken(): String?

    /** Self-heal after a 401: invalidate the local cache so the next [currentIdToken] call is guaranteed to fetch fresh. */
    suspend fun invalidateToken()
}
