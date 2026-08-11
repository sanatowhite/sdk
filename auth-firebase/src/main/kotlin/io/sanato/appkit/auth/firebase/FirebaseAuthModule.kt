package io.sanato.appkit.auth.firebase

import android.app.Application
import android.content.Context
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.sanato.appkit.core.auth.AuthRepository
import io.sanato.appkit.core.auth.AuthTokenProvider
import io.sanato.appkit.core.auth.SessionScopedStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Optional
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Optional override for the Google Sign-In web client id. Consumers can't be
 * relied on to have a real `google-services.json` (the template ships a
 * placeholder with an empty `oauth_client` array — see `TEMPLATE.md`), so
 * this module resolves the id at runtime by name from `:app`'s resources
 * rather than referencing `R.string.default_web_client_id` directly (a
 * library module can't see `:app`'s generated `R` class anyway). A consumer
 * who wants to supply it a different way can bind this instead.
 */
fun interface GoogleWebClientIdOverride {
    fun get(): String
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseAuthBindsModule {
    @BindsOptionalOf
    abstract fun googleWebClientIdOverride(): GoogleWebClientIdOverride

    /**
     * Without this, an app that doesn't register any `@IntoSet SessionScopedStore`
     * binding fails to compile with Dagger's "cannot create an implementation
     * of an interface with type parameters" / empty-multibinding error — the
     * same failure mode already hit by `Set<Telemetry>`/`Set<AppInitializer>`
     * elsewhere in this repo.
     */
    @Multibinds
    abstract fun sessionScopedStores(): Set<SessionScopedStore>
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class AuthExternalScope

@Module
@InstallIn(SingletonComponent::class)
object FirebaseAuthModule {
    @Provides
    @Singleton
    @AuthExternalScope
    fun provideAuthExternalScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Reads `default_web_client_id` by resource name rather than by
     * generated `R` reference — see [GoogleWebClientIdOverride]'s KDoc. Not
     * found (id == 0, i.e. no real `google-services.json` yet) → `null`,
     * which makes [io.sanato.appkit.core.auth.AuthRepository.signInWithGoogle]
     * return `ProviderNotEnabled` instead of crashing.
     */
    @Provides
    @Singleton
    fun provideGoogleWebClientId(
        application: Application,
        override: Optional<GoogleWebClientIdOverride>,
    ): String? =
        override
            .map { it.get() }
            .orElseGet {
                val id = application.resources.getIdentifier("default_web_client_id", "string", application.packageName)
                if (id != 0) application.getString(id) else null
            }

    @Provides
    @Singleton
    fun provideFirebaseAuthRepository(
        @ApplicationContext context: Context,
        googleWebClientId: String?,
        @AuthExternalScope scope: CoroutineScope,
    ): FirebaseAuthRepository = FirebaseAuthRepository(context, googleWebClientId, externalScope = scope)

    @Provides
    @Singleton
    fun provideAuthRepository(impl: FirebaseAuthRepository): AuthRepository = impl

    @Provides
    @Singleton
    fun provideAuthTokenProvider(impl: FirebaseAuthRepository): AuthTokenProvider = impl
}
