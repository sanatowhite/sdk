package io.sanato.appkit.auth.net.hilt

import android.content.Context
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.sanato.appkit.core.auth.AuthTokenProvider
import io.sanato.appkit.core.common.isDebuggableBuild
import io.sanato.appkit.core.net.HttpClientFactory
import io.sanato.appkit.core.net.NetworkMetricsSink
import io.sanato.appkit.core.net.ws.WebSocketTokenProvider
import okhttp3.OkHttpClient
import java.util.Optional
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks the [OkHttpClient] that carries auth headers and self-heals 401s.
 * A consumer who also wants an unauthenticated client (e.g. for a public
 * status endpoint) injects the plain, unqualified one `:core-net` or
 * `:net-telemetry-hilt` would otherwise provide — this module doesn't touch
 * that binding at all.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Authenticated

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthNetBindsModule {
    /**
     * Optional because `:net-telemetry-hilt` is the only module that provides
     * a real `NetworkMetricsSink` binding — a consumer who hasn't pulled it
     * in must still be able to compile this module's graph. Same pattern as
     * `:core-telemetry-hilt`'s handling of `DiagnosticLogSink`.
     */
    @BindsOptionalOf
    abstract fun networkMetricsSink(): NetworkMetricsSink
}

@Module
@InstallIn(SingletonComponent::class)
object AuthNetModule {
    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenProvider: AuthTokenProvider): AuthInterceptor = AuthInterceptor(tokenProvider)

    @Provides
    @Singleton
    fun provideAuthTokenAuthenticator(tokenProvider: AuthTokenProvider): AuthTokenAuthenticator =
        AuthTokenAuthenticator(tokenProvider)

    @Provides
    @Singleton
    fun provideAuthWebSocketTokenProvider(tokenProvider: AuthTokenProvider): WebSocketTokenProvider =
        AuthWebSocketTokenProvider(tokenProvider)

    /**
     * ⚠️ `okHttpClient()`'s signature is frozen by `sanato.api.check` — adding
     * an `authenticator` parameter there (even with a default) would make
     * every existing call site with named/positional args after it ambiguous
     * between overloads, which is a *source*-compatibility break `apiCheck`'s
     * javap diffing can't see. The only non-breaking path is composing here,
     * entirely outside `:core-net`: build the ordinary client via
     * [HttpClientFactory.okHttpClient], then layer the authenticator on with
     * `newBuilder()`. `:core-net` itself is not touched by this module at all.
     */
    @Provides
    @Singleton
    @Authenticated
    fun provideAuthenticatedOkHttpClient(
        interceptor: AuthInterceptor,
        authenticator: AuthTokenAuthenticator,
        metricsSink: Optional<NetworkMetricsSink>,
        @ApplicationContext context: Context,
    ): OkHttpClient =
        HttpClientFactory
            .okHttpClient(
                enableLogging = context.isDebuggableBuild(),
                additionalInterceptors = listOf(interceptor),
                metricsSink = metricsSink.orElse(null),
            ).newBuilder()
            .authenticator(authenticator)
            .build()
}
