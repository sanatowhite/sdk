package io.sanato.appkit.core.net.ws

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException

/**
 * Deterministic stand-in for a real socket, used only by [RealWebSocketConnectionTest].
 * Lets tests drive the state machine (open/fail/close/deliver-message) directly,
 * with zero I/O and zero real time — every attempt is tracked so tests can
 * assert on request headers/URLs per attempt.
 */
internal class FakeWebSocketTransport : WebSocketTransport {
    private val attempts = mutableListOf<Attempt>()

    val requests: List<Request> get() = attempts.map { it.request }

    override fun open(
        request: Request,
        listener: WebSocketListener,
    ): WebSocket {
        val socket = FakeWebSocket(request)
        attempts += Attempt(request, listener, socket)
        return socket
    }

    private fun latest(): Attempt = attempts.last()

    fun completeOpen(code: Int = 101) {
        val attempt = latest()
        attempt.listener.onOpen(attempt.socket, fakeResponse(attempt.request, code))
    }

    /** Simulates a handshake rejected with an HTTP response (e.g. 401) before the upgrade completed. */
    fun rejectHandshake(
        code: Int,
        body: String? = null,
    ) {
        val attempt = latest()
        val response = fakeResponse(attempt.request, code, body)
        attempt.listener.onFailure(attempt.socket, IOException("handshake rejected"), response)
    }

    /** Transport-level failure with no HTTP response at all (DNS, TLS, connection reset...). */
    fun failTransport(throwable: Throwable = IOException("boom")) {
        val attempt = latest()
        attempt.listener.onFailure(attempt.socket, throwable, null)
    }

    fun peerCloses(
        code: Int,
        reason: String = "",
    ) {
        val attempt = latest()
        attempt.listener.onClosed(attempt.socket, code, reason)
    }

    fun deliverText(text: String) {
        val attempt = latest()
        attempt.listener.onMessage(attempt.socket, text)
    }

    fun deliverBinary(bytes: ByteString) {
        val attempt = latest()
        attempt.listener.onMessage(attempt.socket, bytes)
    }

    fun attemptCount(): Int = attempts.size

    private data class Attempt(
        val request: Request,
        val listener: WebSocketListener,
        val socket: FakeWebSocket,
    )
}

internal class FakeWebSocket(
    private val underlyingRequest: Request,
) : WebSocket {
    var cancelled: Boolean = false
        private set
    var closeCode: Int? = null
        private set
    val sentText = mutableListOf<String>()

    override fun request(): Request = underlyingRequest

    override fun queueSize(): Long = 0

    override fun send(text: String): Boolean {
        sentText += text
        return true
    }

    override fun send(bytes: ByteString): Boolean = true

    override fun close(
        code: Int,
        reason: String?,
    ): Boolean {
        closeCode = code
        return true
    }

    override fun cancel() {
        cancelled = true
    }
}

private fun fakeResponse(
    request: Request,
    code: Int,
    body: String? = null,
): Response =
    Response
        .Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("fake")
        .body((body ?: "").toResponseBody("text/plain".toMediaTypeOrNull()))
        .build()
