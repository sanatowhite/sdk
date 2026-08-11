package io.sanato.appkit.core.net.ws

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object WebSocketFactory {
    /**
     * Derives a client suitable for long-lived connections from a shared
     * [base] — same connection pool / dispatcher / DNS / proxy / TLS config,
     * only the timeouts that are fatal to a long-lived socket are overridden.
     *
     * ⚠️ Never hand [HttpClientFactory][io.sanato.appkit.core.net.HttpClientFactory]'s
     * `okHttpClient()` return value directly to a WebSocket call: its
     * `callTimeout(30.seconds)` unconditionally kills the whole call — and a
     * WebSocket session *is* one call — after 30 seconds; its
     * `readTimeout(15.seconds)` throws on an idle-but-healthy connection.
     */
    fun webSocketOkHttpClient(
        base: OkHttpClient,
        pingInterval: Duration = 20.seconds,
    ): OkHttpClient =
        base
            .newBuilder()
            .callTimeout(Duration.ZERO)
            .readTimeout(Duration.ZERO)
            .writeTimeout(10.seconds)
            .pingInterval(pingInterval)
            .build()

    /**
     * @param scope Owned by the caller: it determines the connection's
     *   lifetime, and lets backoff logic be driven fully by virtual time in
     *   `runTest` during tests (pass `backgroundScope`). Pass an app-scoped
     *   `CoroutineScope`, not a `viewModelScope` — a long-lived connection
     *   should not die with one screen.
     * @param onlineSignal Typically `NetworkMonitor(context).isOnline()`. This
     *   deliberately takes a `Flow<Boolean>` rather than a `NetworkMonitor`
     *   type so tests can feed a plain `MutableStateFlow` without Robolectric.
     */
    fun create(
        client: OkHttpClient,
        config: WebSocketConfig,
        scope: CoroutineScope,
        tokenProvider: WebSocketTokenProvider? = null,
        onlineSignal: Flow<Boolean>? = null,
        metricsSink: WebSocketMetricsSink? = null,
    ): WebSocketConnection =
        RealWebSocketConnection(
            transport = { request, listener -> client.newWebSocket(request, listener) },
            config = config,
            scope = scope,
            tokenProvider = tokenProvider,
            onlineSignal = onlineSignal,
            metricsSink = metricsSink,
        )
}
