package io.sanato.appkit.core.net.ws

import io.sanato.appkit.core.common.AppResult
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A managed, auto-reconnecting WebSocket connection. Obtain one via
 * [WebSocketFactory.create] — this interface exists so consumers depend on a
 * contract rather than a constructor, and so tests can substitute a fake.
 */
interface WebSocketConnection {
    val state: StateFlow<WebSocketState>

    /**
     * Hot, `replay = 0`. With zero collectors, messages are dropped — that is
     * the correct semantics for a long-lived connection, not a bug. A slow
     * collector propagates backpressure into the inbound buffer and can
     * trigger [WebSocketConfig.overflowPolicy].
     */
    val messages: SharedFlow<WebSocketMessage>

    /** Idempotent: a no-op while connected/connecting. Restarts from [WebSocketState.Failed]. */
    fun connect()

    /**
     * @return [AppResult.Success] only means the frame entered OkHttp's outbound
     *   queue, **not** that the peer received it. Delivery confirmation is an
     *   application-protocol concern (use an ack message if you need one).
     */
    suspend fun send(message: WebSocketMessage): AppResult<Unit>

    /** Normal shutdown; does not trigger a reconnect. */
    fun close(
        code: Int = 1_000,
        reason: String? = null,
    )

    /**
     * Forces a fresh connection with a newly fetched credential — call this
     * when the auth layer rotates tokens, so a stale connection doesn't wait
     * to be kicked by the server.
     */
    fun reconnect()
}
