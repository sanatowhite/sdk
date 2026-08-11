package io.sanato.appkit.auth.net.hilt

import io.sanato.appkit.core.auth.AuthTokenProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Self-heals a 401 by forcing a fresh token and retrying exactly once.
 *
 * The one deliberate `runBlocking` in this module: OkHttp's
 * [Authenticator.authenticate] contract is documented as allowed to block
 * (it's the designated synchronous extension point for "go get a new
 * credential"), and it never runs on the caller's original thread — unlike
 * [AuthInterceptor], which runs on every request and must stay non-blocking.
 */
class AuthTokenAuthenticator(
    private val tokenProvider: AuthTokenProvider,
) : Authenticator {
    override fun authenticate(
        route: Route?,
        response: Response,
    ): Request? {
        // A prior response on this chain means we already retried once — give up rather
        // than loop forever against a backend that keeps rejecting every token we send.
        if (response.priorResponse != null) return null

        val stale = response.request.header(AuthInterceptor.HEADER)?.removePrefix(AuthInterceptor.PREFIX)
        val fresh =
            runBlocking {
                tokenProvider.invalidateToken()
                tokenProvider.currentIdToken(forceRefresh = true)
            } ?: return null // No credential available (signed out) — let the 401 propagate.

        if (fresh == stale) return null // Same token came back — retrying would just 401 again.

        return response.request
            .newBuilder()
            .header(AuthInterceptor.HEADER, "${AuthInterceptor.PREFIX}$fresh")
            .build()
    }
}
