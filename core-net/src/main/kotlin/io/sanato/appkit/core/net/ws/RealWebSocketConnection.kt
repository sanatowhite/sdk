package io.sanato.appkit.core.net.ws

import io.sanato.appkit.core.common.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import kotlin.random.Random
import kotlin.time.Duration

/**
 * The real state machine behind [WebSocketConnection]. Everything that isn't
 * "talk to a socket" — backoff, auth retry, offline gating, backpressure —
 * lives here and is exercised in tests via a fake [WebSocketTransport], never
 * a real socket.
 *
 * Concurrency model: a single [controlEvents] channel serializes every input
 * — OkHttp listener callbacks (from the reader thread) and public API calls
 * ([connect]/[reconnect]/[close]) — into one coroutine ([runLoop]) that owns
 * all mutable state. Nothing here needs a lock because nothing but that one
 * coroutine ever mutates connection state; the listener and public API only
 * ever *send* into a channel.
 */
internal class RealWebSocketConnection(
    private val transport: WebSocketTransport,
    private val config: WebSocketConfig,
    private val scope: CoroutineScope,
    private val tokenProvider: WebSocketTokenProvider?,
    private val onlineSignal: Flow<Boolean>?,
    private val metricsSink: WebSocketMetricsSink?,
    private val random: Random = Random.Default,
) : WebSocketConnection {
    private val _state = MutableStateFlow<WebSocketState>(WebSocketState.Idle)
    override val state: StateFlow<WebSocketState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<WebSocketMessage>(replay = 0)
    override val messages: SharedFlow<WebSocketMessage> = _messages.asSharedFlow()

    /**
     * Filled by the OkHttp reader thread via non-blocking [Channel.trySend];
     * drained by the pump coroutine started in [init]. This split is what
     * keeps the reader thread from ever blocking on a slow collector of
     * [messages] — see [WebSocketOverflowPolicy].
     */
    private val inbound = Channel<WebSocketMessage>(config.inboundBufferCapacity)

    /** Every input to the state machine, always unbounded (these are cheap control signals, never message payloads). */
    private val controlEvents = Channel<ControlEvent>(Channel.UNLIMITED)

    /**
     * `wss://`/`ws://` is not a scheme `HttpUrl` will parse directly (it only
     * accepts http/https) — routing this through `Request.Builder().url(String)`
     * gets OkHttp's own ws→http / wss→https translation for free, the same
     * translation `okhttp3.OkHttpClient.newWebSocket` relies on internally.
     */
    private val baseUrl =
        Request
            .Builder()
            .url(config.url)
            .build()
            .url

    private val endpoint: String = "${baseUrl.host}${baseUrl.encodedPath}"

    @Volatile private var activeWebSocket: WebSocket? = null

    private var online: Boolean = true
    private var generation = 0
    private var loopJob: Job? = null

    private sealed interface ControlEvent {
        /**
         * Socket-lifecycle events carry the generation of the attempt that
         * produced them; admin commands don't need one.
         */
        data class Open(
            val generation: Int,
            val response: Response,
        ) : ControlEvent

        data class Failure(
            val generation: Int,
            val throwable: Throwable,
            val response: Response?,
        ) : ControlEvent

        data class Ended(
            val generation: Int,
            val code: Int,
            val reason: String,
        ) : ControlEvent

        data class Overflow(
            val generation: Int,
        ) : ControlEvent

        data object StartRequested : ControlEvent

        data object ForceReconnect : ControlEvent

        data class CloseRequested(
            val code: Int,
            val reason: String?,
        ) : ControlEvent

        data class OnlineChanged(
            val isOnline: Boolean,
        ) : ControlEvent
    }

    private sealed interface AttemptOutcome {
        data object Opened : AttemptOutcome

        data class Failed(
            val error: WebSocketError,
        ) : AttemptOutcome

        data class ClosedByUser(
            val code: Int,
            val reason: String?,
        ) : AttemptOutcome

        data object RestartRequested : AttemptOutcome
    }

    private sealed interface SessionEndOutcome {
        data class Clean(
            val code: Int,
            val reason: String,
        ) : SessionEndOutcome

        data class PeerClosed(
            val code: Int,
            val reason: String,
        ) : SessionEndOutcome

        data class Failed(
            val error: WebSocketError,
        ) : SessionEndOutcome

        data class Overflowed(
            val capacity: Int,
        ) : SessionEndOutcome

        data class ClosedByUser(
            val code: Int,
            val reason: String?,
        ) : SessionEndOutcome

        data object RestartRequested : SessionEndOutcome
    }

    init {
        scope.launch { for (message in inbound) _messages.emit(message) }
        onlineSignal?.let { flow ->
            scope.launch { flow.collect { controlEvents.trySend(ControlEvent.OnlineChanged(isOnline = it)) } }
        }
    }

    override fun connect() {
        controlEvents.trySend(ControlEvent.StartRequested)
        ensureLoopStarted()
    }

    override fun reconnect() {
        controlEvents.trySend(ControlEvent.ForceReconnect)
        ensureLoopStarted()
    }

    override fun close(
        code: Int,
        reason: String?,
    ) {
        controlEvents.trySend(ControlEvent.CloseRequested(code, reason))
        ensureLoopStarted()
    }

    override suspend fun send(message: WebSocketMessage): AppResult<Unit> {
        val socket = activeWebSocket ?: return AppResult.Failure(IllegalStateException("not connected"))
        val sent =
            when (message) {
                is WebSocketMessage.Text -> socket.send(message.value)
                is WebSocketMessage.Binary -> socket.send(message.bytes)
            }
        return if (sent) {
            AppResult.Success(Unit)
        } else {
            AppResult.Failure(
                IllegalStateException("failed to enqueue frame: connection is closed or the outbound queue is full"),
            )
        }
    }

    private fun ensureLoopStarted() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { runLoop() }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")
    private suspend fun runLoop() {
        var attempt = 0
        var forceRefreshNext = false
        var authRetryUsed = false
        var running = false

        while (scope.isActive) {
            if (!running) {
                when (val event = controlEvents.receive()) {
                    is ControlEvent.StartRequested, ControlEvent.ForceReconnect -> {
                        running = true
                        authRetryUsed = false
                    }

                    is ControlEvent.CloseRequested -> {
                        activeWebSocket?.close(event.code, event.reason)
                        activeWebSocket = null
                    }

                    is ControlEvent.OnlineChanged -> {
                        online = event.isOnline
                    }

                    // Stale socket-lifecycle callback from an attempt we've already moved past.
                    else -> {}
                }
                continue
            }

            if (attempt >= config.retry.maxAttempts) {
                _state.value =
                    WebSocketState.Failed(WebSocketError.Transport(IOException("max attempts ($attempt) exhausted")))
                running = false
                continue
            }

            generation++
            val gen = generation
            activeWebSocket?.cancel()
            activeWebSocket = null
            _state.value = WebSocketState.Connecting

            val token = tokenProvider?.token(forceRefresh = forceRefreshNext)
            forceRefreshNext = false
            if (tokenProvider != null && token == null) {
                _state.value = WebSocketState.Failed(WebSocketError.Unauthenticated())
                metricsSink?.onWebSocketEvent(WebSocketMetricEvent.Failed(endpoint, "unauthenticated", 0, attempt))
                running = false
                continue
            }

            val handshakeStartMillis = System.currentTimeMillis()
            activeWebSocket = transport.open(request(token), Listener(gen))

            when (val outcome = waitForOpen(gen)) {
                AttemptOutcome.Opened -> {
                    metricsSink?.onWebSocketEvent(
                        WebSocketMetricEvent.Opened(
                            endpoint,
                            System.currentTimeMillis() - handshakeStartMillis,
                            attempt,
                        ),
                    )
                    _state.value = WebSocketState.Connected
                    val connectedAtMillis = System.currentTimeMillis()
                    val stableJob =
                        scope.launch {
                            delay(config.retry.resetAfterConnectedFor)
                            attempt = 0
                            authRetryUsed = false
                        }

                    val sessionEnd = waitForSessionEnd(gen)
                    stableJob.cancel()
                    activeWebSocket = null
                    val sessionMillis = System.currentTimeMillis() - connectedAtMillis

                    when (sessionEnd) {
                        is SessionEndOutcome.Clean -> {
                            _state.value = WebSocketState.Closed(sessionEnd.code, sessionEnd.reason)
                            metricsSink?.onWebSocketEvent(closedEvent(sessionEnd.code, sessionMillis, clean = true))
                            attempt = 0
                            running = false
                        }

                        is SessionEndOutcome.PeerClosed -> {
                            metricsSink?.onWebSocketEvent(closedEvent(sessionEnd.code, sessionMillis, clean = false))
                            val error = WebSocketError.ClosedByPeer(sessionEnd.code, sessionEnd.reason)
                            attempt++
                            val (keepRunning, refreshUsed) =
                                handleFailure(error, attempt, authRetryUsed) { forceRefreshNext = it }
                            authRetryUsed = refreshUsed
                            running = keepRunning
                        }

                        is SessionEndOutcome.Failed -> {
                            attempt++
                            metricsSink?.onWebSocketEvent(
                                WebSocketMetricEvent.Failed(
                                    endpoint,
                                    sessionEnd.error.message.orEmpty(),
                                    sessionMillis,
                                    attempt,
                                ),
                            )
                            val (keepRunning, refreshUsed) =
                                handleFailure(sessionEnd.error, attempt, authRetryUsed) { forceRefreshNext = it }
                            authRetryUsed = refreshUsed
                            running = keepRunning
                        }

                        is SessionEndOutcome.Overflowed -> {
                            val error = WebSocketError.BackpressureOverflow(sessionEnd.capacity)
                            attempt++
                            metricsSink?.onWebSocketEvent(
                                WebSocketMetricEvent.Failed(endpoint, error.message.orEmpty(), sessionMillis, attempt),
                            )
                            val (keepRunning, refreshUsed) =
                                handleFailure(error, attempt, authRetryUsed) { forceRefreshNext = it }
                            authRetryUsed = refreshUsed
                            running = keepRunning
                        }

                        is SessionEndOutcome.ClosedByUser -> {
                            _state.value = WebSocketState.Closed(sessionEnd.code, sessionEnd.reason.orEmpty())
                            attempt = 0
                            running = false
                        }

                        SessionEndOutcome.RestartRequested -> {
                            // A deliberate reconnect() call — not a failure, so no backoff and no attempt increment.
                        }
                    }
                }

                is AttemptOutcome.Failed -> {
                    attempt++
                    metricsSink?.onWebSocketEvent(
                        WebSocketMetricEvent.Failed(endpoint, outcome.error.message.orEmpty(), 0, attempt),
                    )
                    val (keepRunning, refreshUsed) =
                        handleFailure(outcome.error, attempt, authRetryUsed) { forceRefreshNext = it }
                    authRetryUsed = refreshUsed
                    running = keepRunning
                }

                is AttemptOutcome.ClosedByUser -> {
                    activeWebSocket?.cancel()
                    activeWebSocket = null
                    _state.value = WebSocketState.Closed(outcome.code, outcome.reason.orEmpty())
                    attempt = 0
                    running = false
                }

                AttemptOutcome.RestartRequested -> {
                    // Loop again immediately with a fresh generation; not a failure.
                }
            }
        }
    }

    /**
     * Shared decision point for "a connection attempt just failed, now what":
     * classify terminal-vs-retryable, apply the "force-refresh exactly once
     * on 401/403" rule, and either wait out the backoff (returning `true`,
     * meaning the caller should keep `running`) or give up (returning
     * `false`).
     */
    private suspend fun handleFailure(
        error: WebSocketError,
        attempt: Int,
        authRetryUsedSoFar: Boolean = false,
        setForceRefresh: (Boolean) -> Unit,
    ): Pair<Boolean, Boolean> {
        var authRetryUsed = authRetryUsedSoFar
        if (error is WebSocketError.HandshakeRejected && error.code in AUTH_REJECTION_CODES) {
            if (!authRetryUsed) {
                authRetryUsed = true
                setForceRefresh(true)
            } else {
                _state.value = WebSocketState.Failed(error)
                return false to authRetryUsed
            }
        } else if (isTerminal(error)) {
            _state.value = WebSocketState.Failed(error)
            return false to authRetryUsed
        }
        val keepGoing = scheduleRetry(attempt, error)
        return keepGoing to authRetryUsed
    }

    /**
     * Waits out the backoff delay for [attempt], or returns early on an
     * online transition / explicit `connect()`/`reconnect()`. While offline,
     * the backoff timer is paused entirely (no attempts are "spent" being
     * offline). Returns `false` only if a `close()` arrived, meaning the
     * caller should go idle instead of retrying.
     *
     * `online` is re-checked on every iteration, not just once at entry —
     * going offline mid-countdown must park the wait immediately (no more
     * ticks consumed), and coming back online — whether we started offline
     * or went offline partway through — always reconnects right away rather
     * than resuming (or restarting) the remaining backoff. That "just try
     * now" behavior is deliberate: a network transition is itself a strong
     * signal that retrying is worth it immediately.
     */
    private suspend fun scheduleRetry(
        attempt: Int,
        cause: WebSocketError,
    ): Boolean {
        var remaining = if (online) backoffMillis(attempt, config.retry, random) else 0L
        _state.value = WebSocketState.Reconnecting(attempt, remaining, cause)

        while (true) {
            if (!online) {
                when (val event = controlEvents.receive()) {
                    is ControlEvent.OnlineChanged -> {
                        online = event.isOnline
                        if (online) return true
                    }

                    is ControlEvent.CloseRequested -> {
                        activeWebSocket?.close(event.code, event.reason)
                        activeWebSocket = null
                        return false
                    }

                    is ControlEvent.ForceReconnect, ControlEvent.StartRequested -> {
                        return true
                    }

                    else -> {}
                }
                continue
            }

            if (remaining <= 0) return true
            val tick = minOf(RETRY_POLL_INTERVAL_MILLIS, remaining)
            when (val event = controlEvents.tryReceive().getOrNull()) {
                is ControlEvent.OnlineChanged -> {
                    online = event.isOnline
                    if (!online) _state.value = WebSocketState.Reconnecting(attempt, 0, cause)
                }

                is ControlEvent.ForceReconnect, ControlEvent.StartRequested -> {
                    return true
                }

                is ControlEvent.CloseRequested -> {
                    activeWebSocket?.close(event.code, event.reason)
                    activeWebSocket = null
                    return false
                }

                else -> {}
            }
            if (online) {
                delay(tick)
                remaining -= tick
            }
        }
    }

    private suspend fun waitForOpen(gen: Int): AttemptOutcome {
        while (true) {
            when (val event = controlEvents.receive()) {
                is ControlEvent.Open -> {
                    if (event.generation == gen) return AttemptOutcome.Opened
                }

                is ControlEvent.Failure -> {
                    if (event.generation == gen) {
                        return AttemptOutcome.Failed(classifyFailure(event.throwable, event.response))
                    }
                }

                is ControlEvent.Ended -> {
                    if (event.generation == gen) {
                        return AttemptOutcome.Failed(
                            WebSocketError.Transport(IOException("closed before handshake completed")),
                        )
                    }
                }

                // Can't overflow before the handshake completes.
                is ControlEvent.Overflow -> {}

                is ControlEvent.CloseRequested -> {
                    return AttemptOutcome.ClosedByUser(event.code, event.reason)
                }

                ControlEvent.ForceReconnect -> {
                    return AttemptOutcome.RestartRequested
                }

                // Already running; a duplicate connect() is a no-op.
                ControlEvent.StartRequested -> {}

                is ControlEvent.OnlineChanged -> {
                    online = event.isOnline
                }
            }
        }
    }

    private suspend fun waitForSessionEnd(gen: Int): SessionEndOutcome {
        while (true) {
            when (val event = controlEvents.receive()) {
                is ControlEvent.Ended -> {
                    if (event.generation == gen) {
                        return if (event.code == NORMAL_CLOSURE_CODE) {
                            SessionEndOutcome.Clean(event.code, event.reason)
                        } else {
                            SessionEndOutcome.PeerClosed(event.code, event.reason)
                        }
                    }
                }

                is ControlEvent.Failure -> {
                    if (event.generation == gen) {
                        return SessionEndOutcome.Failed(classifyFailure(event.throwable, event.response))
                    }
                }

                is ControlEvent.Overflow -> {
                    if (event.generation == gen) {
                        return SessionEndOutcome.Overflowed(config.inboundBufferCapacity)
                    }
                }

                // Stale — a handshake-phase event can't arrive once we're past waitForOpen.
                is ControlEvent.Open -> {}

                is ControlEvent.CloseRequested -> {
                    return SessionEndOutcome.ClosedByUser(event.code, event.reason)
                }

                ControlEvent.ForceReconnect -> {
                    return SessionEndOutcome.RestartRequested
                }

                // Already running; a duplicate connect() is a no-op.
                ControlEvent.StartRequested -> {}

                is ControlEvent.OnlineChanged -> {
                    online = event.isOnline
                }
            }
        }
    }

    private fun isTerminal(error: WebSocketError): Boolean =
        when (error) {
            is WebSocketError.Unauthenticated -> true
            is WebSocketError.HandshakeRejected -> error.code in TERMINAL_HANDSHAKE_CODES
            is WebSocketError.ClosedByPeer -> error.code in TERMINAL_CLOSE_CODES
            else -> false
        }

    private fun classifyFailure(
        throwable: Throwable,
        response: Response?,
    ): WebSocketError =
        if (response != null) {
            WebSocketError.HandshakeRejected(response.code, runCatching { response.body.string() }.getOrNull())
        } else {
            WebSocketError.Transport(throwable)
        }

    private fun closedEvent(
        code: Int,
        sessionMillis: Long,
        clean: Boolean,
    ) = WebSocketMetricEvent.Closed(
        endpoint = endpoint,
        code = code,
        sessionMillis = sessionMillis,
        messagesIn = 0,
        messagesOut = 0,
        bytesIn = 0,
        bytesOut = 0,
        clean = clean,
    )

    private fun request(token: String?): Request {
        val url =
            if (token != null && config.tokenPlacement is TokenPlacement.QueryParameter) {
                baseUrl.newBuilder().addQueryParameter(config.tokenPlacement.name, token).build()
            } else {
                baseUrl
            }
        val builder = Request.Builder().url(url)
        config.headers.forEach { (name, value) -> builder.addHeader(name, value) }
        if (token != null) {
            when (val placement = config.tokenPlacement) {
                is TokenPlacement.Header -> {
                    builder.addHeader(placement.name, "${placement.prefix}$token")
                }

                is TokenPlacement.Subprotocol -> {
                    builder.addHeader(
                        "Sec-WebSocket-Protocol",
                        "${placement.prefix}$token",
                    )
                }

                // Already applied to the URL above.
                is TokenPlacement.QueryParameter -> {}
            }
        }
        return builder.build()
    }

    private inner class Listener(
        private val listenerGeneration: Int,
    ) : WebSocketListener() {
        override fun onOpen(
            webSocket: WebSocket,
            response: Response,
        ) {
            controlEvents.trySend(ControlEvent.Open(listenerGeneration, response))
        }

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            enqueueInbound(WebSocketMessage.Text(text))
        }

        override fun onMessage(
            webSocket: WebSocket,
            bytes: ByteString,
        ) {
            enqueueInbound(WebSocketMessage.Binary(bytes))
        }

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            webSocket.close(code, reason)
        }

        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            controlEvents.trySend(ControlEvent.Ended(listenerGeneration, code, reason))
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            controlEvents.trySend(ControlEvent.Failure(listenerGeneration, t, response))
        }

        /**
         * Runs on OkHttp's reader thread for this socket. Must never block
         * except under [WebSocketOverflowPolicy.SUSPEND_READER], which blocks
         * on purpose — see that enum entry's KDoc.
         */
        private fun enqueueInbound(message: WebSocketMessage) {
            val result = inbound.trySend(message)
            if (result.isSuccess) return
            when (config.overflowPolicy) {
                WebSocketOverflowPolicy.FAIL_CONNECTION -> {
                    controlEvents.trySend(ControlEvent.Overflow(listenerGeneration))
                }

                WebSocketOverflowPolicy.DROP_OLDEST -> {
                    inbound.tryReceive()
                    inbound.trySend(message)
                }

                WebSocketOverflowPolicy.SUSPEND_READER -> {
                    runBlocking { inbound.send(message) }
                }
            }
        }
    }

    private companion object {
        const val NORMAL_CLOSURE_CODE = 1_000
        const val RETRY_POLL_INTERVAL_MILLIS = 20L
        val AUTH_REJECTION_CODES = setOf(401, 403)
        val TERMINAL_HANDSHAKE_CODES = setOf(400, 403, 404, 426)
        val TERMINAL_CLOSE_CODES = setOf(1_000, 1_003, 1_008)
    }
}

internal fun backoffMillis(
    attempt: Int,
    policy: WebSocketRetryPolicy,
    random: Random,
): Long {
    val exponent = (attempt - 1).coerceAtLeast(0)
    val raw: Duration = policy.initialDelay * powInt(policy.multiplier, exponent)
    val capped = if (raw > policy.maxDelay) policy.maxDelay else raw
    val jitterFactor =
        if (policy.jitterRatio <= 0.0) {
            1.0
        } else {
            1.0 + random.nextDouble(-policy.jitterRatio, policy.jitterRatio)
        }
    return (capped.inWholeMilliseconds * jitterFactor).toLong().coerceAtLeast(0)
}

private fun powInt(
    base: Double,
    exponent: Int,
): Double {
    var result = 1.0
    repeat(exponent) { result *= base }
    return result
}
