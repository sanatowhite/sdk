package io.sanato.appkit.core.net.ws

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Layer 2: real sockets via `mockwebserver3`, checking exactly the things
 * Layer 1's fake transport can't — actual bytes on the wire (header/subprotocol
 * placement, query-string exclusion from telemetry) and that
 * [WebSocketFactory.webSocketOkHttpClient] really does override the fatal
 * timeouts. No virtual time here: `runTest`'s virtual clock only advances
 * coroutines parked on the test dispatcher, and MockWebServer's I/O runs on
 * real threads — mixing the two gives flaky, not deterministic, tests.
 */
class WebSocketFactoryTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        runCatching { server.close() }
    }

    @Test
    fun `token is sent as an Authorization header by default`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().webSocketUpgrade(RecordingWebSocketListener()).build())
            val connection = connect(TokenPlacement.Header())

            awaitState<WebSocketState.Connected>(connection)
            val recorded = server.takeRequest()
            assertEquals("Bearer test-token", recorded.headers["Authorization"])

            connection.close()
        }

    @Test
    fun `token is sent via Sec-WebSocket-Protocol when placement is Subprotocol`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().webSocketUpgrade(RecordingWebSocketListener()).build())
            val connection = connect(TokenPlacement.Subprotocol())

            awaitState<WebSocketState.Connected>(connection)
            val recorded = server.takeRequest()
            assertEquals("bearer.test-token", recorded.headers["Sec-WebSocket-Protocol"])

            connection.close()
        }

    @Test
    fun `token as a query parameter never leaks into the metrics endpoint label`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().webSocketUpgrade(RecordingWebSocketListener()).build())
            val events = Channel<WebSocketMetricEvent>(Channel.UNLIMITED)
            val connection =
                connect(TokenPlacement.QueryParameter(), metricsSink = WebSocketMetricsSink { events.trySend(it) })

            awaitState<WebSocketState.Connected>(connection)
            val recorded = server.takeRequest()
            assertTrue(recorded.target.contains("access_token=test-token"))

            val opened = withTimeout(5.seconds) { events.receive() } as WebSocketMetricEvent.Opened
            assertFalse("endpoint leaked the token: ${opened.endpoint}", opened.endpoint.contains("test-token"))
            assertFalse("endpoint leaked the query string: ${opened.endpoint}", opened.endpoint.contains("?"))

            connection.close()
        }

    @Test
    fun `a 401 response to the upgrade maps to HandshakeRejected`() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(401)
                    .body("nope")
                    .build(),
            )
            val connection = connect(TokenPlacement.Header())

            val failed =
                withTimeout(5.seconds) {
                    connection.state.first { it is WebSocketState.Failed || it is WebSocketState.Reconnecting }
                }
            val error =
                when (failed) {
                    is WebSocketState.Failed -> failed.error
                    is WebSocketState.Reconnecting -> failed.cause
                    else -> error("unreachable")
                }
            val rejected = error as WebSocketError.HandshakeRejected
            assertEquals(401, rejected.code)

            connection.close()
        }

    @Test
    fun `webSocketOkHttpClient overrides the base client's call and read timeouts`() =
        runBlocking {
            // A base client with a callTimeout far shorter than how long we'll keep the connection open.
            // Without WebSocketFactory's override, OkHttp would unconditionally kill the whole call —
            // and a WebSocket session *is* one call — well before this test's assertions run.
            val shortTimeoutBase =
                OkHttpClient
                    .Builder()
                    .callTimeout(300.milliseconds)
                    .readTimeout(300.milliseconds)
                    .build()
            val wsClient = WebSocketFactory.webSocketOkHttpClient(shortTimeoutBase, pingInterval = 10.seconds)

            server.enqueue(MockResponse.Builder().webSocketUpgrade(RecordingWebSocketListener()).build())
            val connection =
                WebSocketFactory.create(
                    client = wsClient,
                    config = WebSocketConfig(url = server.url("/socket").toString()),
                    scope = scope,
                )
            connection.connect()
            awaitState<WebSocketState.Connected>(connection)

            // Outlive the base client's 300ms callTimeout by a wide margin.
            kotlinx.coroutines.delay(1.seconds)
            assertEquals(WebSocketState.Connected, connection.state.value)

            connection.close()
        }

    private fun connect(
        placement: TokenPlacement,
        metricsSink: WebSocketMetricsSink? = null,
    ): WebSocketConnection {
        val client = WebSocketFactory.webSocketOkHttpClient(OkHttpClient())
        val connection =
            WebSocketFactory.create(
                client = client,
                config =
                    WebSocketConfig(
                        url = server.url("/socket").toString(),
                        tokenPlacement = placement,
                    ),
                scope = scope,
                tokenProvider = WebSocketTokenProvider { "test-token" },
                metricsSink = metricsSink,
            )
        connection.connect()
        return connection
    }

    private suspend inline fun <reified T : WebSocketState> awaitState(connection: WebSocketConnection) {
        withTimeout(5.seconds) { connection.state.first { it is T } }
    }

    private class RecordingWebSocketListener : WebSocketListener() {
        override fun onOpen(
            webSocket: WebSocket,
            response: okhttp3.Response,
        ) = Unit
    }
}
