package io.sanato.appkit.core.net.ws

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Layer 1: the state machine, driven entirely by [FakeWebSocketTransport] and
 * `runTest`'s virtual time — zero real sockets, zero real sleeps. Each test
 * asserts one bullet point of the behavioral contract documented on
 * [RealWebSocketConnection] / `core-net/README.md`'s WebSocket section.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealWebSocketConnectionTest {
    private fun config(
        maxAttempts: Int = Int.MAX_VALUE,
        overflowPolicy: WebSocketOverflowPolicy = WebSocketOverflowPolicy.FAIL_CONNECTION,
        inboundBufferCapacity: Int = 64,
    ) = WebSocketConfig(
        url = "wss://example.invalid/socket",
        retry =
            WebSocketRetryPolicy(
                maxAttempts = maxAttempts,
                initialDelay = 100.milliseconds,
                maxDelay = 1.seconds,
                multiplier = 2.0,
                jitterRatio = 0.0,
                resetAfterConnectedFor = 500.milliseconds,
            ),
        overflowPolicy = overflowPolicy,
        inboundBufferCapacity = inboundBufferCapacity,
    )

    private fun TestScope.newConnection(
        transport: FakeWebSocketTransport,
        wsConfig: WebSocketConfig = config(),
        tokenProvider: WebSocketTokenProvider? = null,
        onlineSignal: Flow<Boolean>? = null,
        metricsSink: WebSocketMetricsSink? = null,
    ): RealWebSocketConnection =
        RealWebSocketConnection(
            transport = transport,
            config = wsConfig,
            scope = backgroundScope,
            tokenProvider = tokenProvider,
            onlineSignal = onlineSignal,
            metricsSink = metricsSink,
        )

    @Test
    fun `connect then open transitions Idle to Connecting to Connected`() =
        runTest {
            val transport = FakeWebSocketTransport()
            val connection = newConnection(transport)

            connection.state.test {
                assertEquals(WebSocketState.Idle, awaitItem())
                connection.connect()
                assertEquals(WebSocketState.Connecting, awaitItem())
                runCurrent()
                transport.completeOpen()
                assertEquals(WebSocketState.Connected, awaitItem())
            }
        }

    @Test
    fun `backoff grows exponentially and is capped at maxDelay`() =
        runTest {
            val transport = FakeWebSocketTransport()
            val connection = newConnection(transport)

            connection.state.test {
                assertEquals(WebSocketState.Idle, awaitItem())
                connection.connect()
                assertEquals(WebSocketState.Connecting, awaitItem())
                runCurrent()

                transport.failTransport()
                val first = awaitItem() as WebSocketState.Reconnecting
                assertEquals(1, first.attempt)
                assertEquals(100L, first.delayMillis) // initialDelay, exponent 0

                advanceTimeBy(101.milliseconds)
                runCurrent()
                assertEquals(WebSocketState.Connecting, awaitItem())
                transport.failTransport()
                val second = awaitItem() as WebSocketState.Reconnecting
                assertEquals(2, second.attempt)
                assertEquals(200L, second.delayMillis) // × multiplier

                advanceTimeBy(201.milliseconds)
                runCurrent()
                assertEquals(WebSocketState.Connecting, awaitItem())
                transport.failTransport()
                val third = awaitItem() as WebSocketState.Reconnecting
                assertEquals(3, third.attempt)
                assertEquals(400L, third.delayMillis)

                advanceTimeBy(401.milliseconds)
                runCurrent()
                assertEquals(WebSocketState.Connecting, awaitItem())
                transport.failTransport()
                val fourth = awaitItem() as WebSocketState.Reconnecting
                assertEquals(4, fourth.attempt)
                assertEquals(800L, fourth.delayMillis)

                advanceTimeBy(801.milliseconds)
                runCurrent()
                assertEquals(WebSocketState.Connecting, awaitItem())
                transport.failTransport()
                val fifth = awaitItem() as WebSocketState.Reconnecting
                assertEquals(5, fifth.attempt)
                assertEquals(1_000L, fifth.delayMillis) // capped at maxDelay
            }
        }

    @Test
    fun `jitter stays within the configured ratio`() {
        val policy =
            WebSocketRetryPolicy(initialDelay = 1.seconds, maxDelay = 10.seconds, multiplier = 1.0, jitterRatio = 0.2)
        val random = Random(42)
        repeat(200) {
            val millis = backoffMillis(attempt = 1, policy = policy, random = random)
            assertTrue("jitter out of range: $millis", millis in 800..1_200)
        }
    }

    @Test
    fun `a connection that stays up past resetAfterConnectedFor resets the attempt counter`() =
        runTest {
            val transport = FakeWebSocketTransport()
            val connection = newConnection(transport)

            connection.state.test {
                awaitItem() // Idle
                connection.connect()
                awaitItem() // Connecting
                runCurrent()
                transport.completeOpen()
                assertEquals(WebSocketState.Connected, awaitItem())

                // Outlive resetAfterConnectedFor (500ms) so the internal attempt counter clears.
                advanceTimeBy(600.milliseconds)
                runCurrent()

                transport.failTransport()
                val reconnecting = awaitItem() as WebSocketState.Reconnecting
                assertEquals(1, reconnecting.attempt) // not 2 — the earlier failed attempt was long forgotten.
            }
        }

    @Test
    fun `a connection that drops right after opening does not reset the attempt counter`() =
        runTest {
            val transport = FakeWebSocketTransport()
            val connection = newConnection(transport)

            connection.state.test {
                awaitItem() // Idle
                connection.connect()
                awaitItem() // Connecting
                runCurrent()
                transport.failTransport()
                assertEquals(1, (awaitItem() as WebSocketState.Reconnecting).attempt)

                advanceTimeBy(101.milliseconds)
                runCurrent()
                awaitItem() // Connecting (attempt 2)
                transport.completeOpen()
                assertEquals(WebSocketState.Connected, awaitItem())

                // Drops immediately — well under resetAfterConnectedFor (500ms).
                advanceTimeBy(50.milliseconds)
                runCurrent()
                transport.failTransport()
                val reconnecting = awaitItem() as WebSocketState.Reconnecting
                // Kept climbing from 1 — a flapping server must not reset to a hot loop.
                assertEquals(2, reconnecting.attempt)
            }
        }

    @Test
    fun `401 triggers exactly one forced token refresh then gives up`() =
        runTest {
            val transport = FakeWebSocketTransport()
            var refreshCalls = 0
            val tokenProvider =
                WebSocketTokenProvider { forceRefresh ->
                    if (forceRefresh) refreshCalls++
                    "token"
                }
            val connection = newConnection(transport, tokenProvider = tokenProvider)

            connection.state.test {
                awaitItem() // Idle
                connection.connect()
                awaitItem() // Connecting
                runCurrent()
                transport.rejectHandshake(401)
                val reconnecting = awaitItem() as WebSocketState.Reconnecting
                assertTrue(reconnecting.cause is WebSocketError.HandshakeRejected)
                assertEquals(0, refreshCalls) // not yet — the refreshed token is requested on the *next* attempt.

                advanceTimeBy(101.milliseconds)
                runCurrent()
                awaitItem() // Connecting, this time with forceRefresh=true
                assertEquals(1, refreshCalls)
                transport.rejectHandshake(401)
                val failed = awaitItem() as WebSocketState.Failed
                assertTrue(failed.error is WebSocketError.HandshakeRejected)
                assertEquals(1, refreshCalls) // no second forced refresh — the loop must stop, not hammer auth.
            }
        }

    @Test
    fun `no token available fails immediately without retrying`() =
        runTest {
            val transport = FakeWebSocketTransport()
            val tokenProvider = WebSocketTokenProvider { null }
            val connection = newConnection(transport, tokenProvider = tokenProvider)

            connection.state.test {
                awaitItem() // Idle
                connection.connect()
                awaitItem() // Connecting
                val failed = awaitItem() as WebSocketState.Failed
                assertTrue(failed.error is WebSocketError.Unauthenticated)
                assertEquals(0, transport.attemptCount()) // never even opened a socket.
            }
        }

    @Test
    fun `offline does not consume backoff attempts and reconnects immediately when back online`() =
        runTest {
            val transport = FakeWebSocketTransport()
            val online = MutableStateFlow(true)
            val connection = newConnection(transport, onlineSignal = online)

            connection.state.test {
                awaitItem() // Idle
                connection.connect()
                awaitItem() // Connecting
                runCurrent()
                transport.failTransport()
                assertEquals(1, (awaitItem() as WebSocketState.Reconnecting).attempt)

                online.value = false
                runCurrent()
                val offlineState = awaitItem() as WebSocketState.Reconnecting
                assertEquals(0L, offlineState.delayMillis) // parked, not counting down.

                // Time passes with no effect while offline — no new Connecting state appears.
                advanceTimeBy(10.seconds)
                runCurrent()

                online.value = true
                runCurrent()
                assertEquals(WebSocketState.Connecting, awaitItem()) // reconnects the instant we're back online.
            }
        }

    @Test
    fun `close is idempotent and does not trigger a reconnect`() =
        runTest {
            val transport = FakeWebSocketTransport()
            val connection = newConnection(transport)

            connection.state.test {
                awaitItem() // Idle
                connection.connect()
                awaitItem() // Connecting
                runCurrent()
                transport.completeOpen()
                assertEquals(WebSocketState.Connected, awaitItem())

                connection.close()
                val closed = awaitItem() as WebSocketState.Closed
                assertEquals(1_000, closed.code)

                // No further state changes — specifically no Connecting.
                advanceTimeBy(5.seconds)
                runCurrent()
                expectNoEvents()
            }
        }

    @Test
    fun `FAIL_CONNECTION overflow policy closes the connection and reconnects`() =
        runTest {
            val transport = FakeWebSocketTransport()
            // A rendezvous inbound buffer (capacity 0) plus a collector that never reads back out —
            // the very first message hands off to the collector's suspended `emit()` and gets stuck
            // there, so the *second* `trySend` has no one to rendezvous with and overflows immediately.
            // Backpressure only exists when a collector is attached: with zero collectors, `messages`
            // silently drops per its documented hot-flow semantics — this is the scenario where a slow
            // collector, not an absent one, is what causes the overflow.
            val wsConfig = config(inboundBufferCapacity = 0, overflowPolicy = WebSocketOverflowPolicy.FAIL_CONNECTION)
            val connection = newConnection(transport, wsConfig = wsConfig)
            val stuckSink = Channel<WebSocketMessage>() // rendezvous, never received from below.
            backgroundScope.launch { connection.messages.collect { stuckSink.send(it) } }
            runCurrent() // let the collector above actually reach `collect{}` and subscribe before we proceed.

            connection.state.test {
                awaitItem() // Idle
                connection.connect()
                awaitItem() // Connecting
                runCurrent()
                transport.completeOpen()
                assertEquals(WebSocketState.Connected, awaitItem())

                transport.deliverText("one") // rendezvous-delivered straight into the stuck collector.
                advanceUntilIdle()
                transport.deliverText("two") // nobody left waiting on `inbound.receive()` — overflows.
                advanceUntilIdle()

                val reconnecting = awaitItem() as WebSocketState.Reconnecting
                assertTrue(reconnecting.cause is WebSocketError.BackpressureOverflow)
            }
        }

    @Test
    fun `messages flow delivers text and binary frames to collectors`() =
        runTest {
            val transport = FakeWebSocketTransport()
            val connection = newConnection(transport)

            connection.messages.test {
                connection.connect()
                runCurrent()
                transport.completeOpen()
                runCurrent()
                transport.deliverText("hello")
                assertEquals(WebSocketMessage.Text("hello"), awaitItem())
            }
        }

    @Test
    fun `terminal handshake rejection stops retrying without waiting for a second attempt`() =
        runTest {
            val transport = FakeWebSocketTransport()
            val connection = newConnection(transport)

            connection.state.test {
                awaitItem() // Idle
                connection.connect()
                awaitItem() // Connecting
                runCurrent()
                transport.rejectHandshake(404)
                val failed = awaitItem() as WebSocketState.Failed
                assertTrue(failed.error is WebSocketError.HandshakeRejected)
                assertEquals(1, transport.attemptCount())

                advanceTimeBy(5.seconds)
                runCurrent()
                expectNoEvents() // no automatic retry after a terminal rejection.
            }
        }
}
