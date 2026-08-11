package io.sanato.appkit.auth.net.hilt

import io.sanato.appkit.core.auth.AuthTokenProvider
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the current auth token to every request, via the
 * `additionalInterceptors` parameter [io.sanato.appkit.core.net.HttpClientFactory.okHttpClient]
 * already exposes — `:core-net` needs zero changes for this to work.
 *
 * Deliberately non-blocking: [Interceptor.intercept] isn't `suspend`, and
 * calling `runBlocking` on every request would introduce a real per-request
 * cost just to shave the rare cold-start 401 (see [AuthTokenAuthenticator] for
 * where the one unavoidable blocking call in this module lives, and why it's
 * fine there but not here).
 */
class AuthInterceptor(
    private val tokenProvider: AuthTokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Already has an Authorization header (caller set it manually) — don't overwrite it.
        if (request.header(HEADER) != null) return chain.proceed(request)

        val token = tokenProvider.cachedIdToken() ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(HEADER, "$PREFIX$token").build())
    }

    companion object {
        const val HEADER: String = "Authorization"
        const val PREFIX: String = "Bearer "
    }
}
