package io.sanato.appkit.auth.net.hilt

import io.sanato.appkit.core.auth.AuthTokenProvider
import io.sanato.appkit.core.net.ws.WebSocketTokenProvider

/**
 * `WebSocketTokenProvider` (the interface) is owned by `:core-net`'s `ws`
 * subpackage — same reasoning as `NetworkMetricsSink`: `:core-net` needs an
 * outlet it can call without depending on auth. This is the bridge
 * implementation, symmetrical to [AuthInterceptor]/[AuthTokenAuthenticator]
 * on the HTTP side.
 */
class AuthWebSocketTokenProvider(
    private val tokenProvider: AuthTokenProvider,
) : WebSocketTokenProvider {
    override suspend fun token(forceRefresh: Boolean): String? = tokenProvider.currentIdToken(forceRefresh)
}
