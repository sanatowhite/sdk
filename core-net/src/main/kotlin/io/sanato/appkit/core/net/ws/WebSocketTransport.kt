package io.sanato.appkit.core.net.ws

import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Internal seam between [RealWebSocketConnection]'s state machine and the real
 * socket. Never part of the published API (`internal`, no golden entry) —
 * its only purpose is letting tests drive the state machine deterministically
 * with a fake, instead of a real socket, so backoff/reconnect tests run in
 * virtual time with zero I/O.
 */
internal fun interface WebSocketTransport {
    fun open(
        request: Request,
        listener: WebSocketListener,
    ): WebSocket
}
