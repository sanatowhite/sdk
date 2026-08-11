package io.sanato.appkit.download.hilt

import android.content.Context
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.sanato.appkit.core.common.isDebuggableBuild
import io.sanato.appkit.core.net.HttpClientFactory
import io.sanato.appkit.core.net.NetworkMetricsSink
import io.sanato.appkit.download.DownloadConfig
import io.sanato.appkit.download.Downloader
import io.sanato.appkit.download.notify.DownloadNotifier
import java.util.Optional
import javax.inject.Singleton

/** Consumer override point — bind this to swap the default `DownloadConfig.default(context)`. */
data class DownloadConfigOverride(
    val config: DownloadConfig,
)

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadBindsModule {
    /**
     * Optional because `:net-telemetry-hilt` is the only module that provides
     * a real [NetworkMetricsSink] binding — a consumer who hasn't pulled it in
     * must still be able to compile this module's graph. Same pattern as
     * `:auth-net-hilt`'s `AuthNetBindsModule`.
     */
    @BindsOptionalOf
    abstract fun networkMetricsSink(): NetworkMetricsSink

    @BindsOptionalOf
    abstract fun downloadConfigOverride(): DownloadConfigOverride

    /** Bind a custom implementation to replace `notify.AndroidDownloadNotifier` entirely. */
    @BindsOptionalOf
    abstract fun downloadNotifierOverride(): DownloadNotifier
}

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    /**
     * `Downloader` is already its own process-wide singleton (see its KDoc —
     * `notify.DownloadService` has to look it up by static accessor since the
     * OS instantiates services, not Hilt). Wrapping [Downloader.getInstance]
     * in `@Singleton` here doesn't create a second instance; it just gives
     * Hilt consumers constructor injection instead of the manual accessor,
     * and — because this provider is the *first* caller in any app that pulls
     * in this module — is where `DownloadConfigOverride`/`DownloadNotifier`
     * bindings actually take effect (see [Downloader.getInstance]'s "first
     * caller wins" KDoc).
     */
    @Provides
    @Singleton
    fun provideDownloader(
        @ApplicationContext context: Context,
        metricsSink: Optional<NetworkMetricsSink>,
        configOverride: Optional<DownloadConfigOverride>,
        notifierOverride: Optional<DownloadNotifier>,
    ): Downloader {
        val config = configOverride.map { it.config }.orElseGet { DownloadConfig.default(context) }
        val client =
            Downloader.downloadOkHttpClient(
                HttpClientFactory.okHttpClient(
                    enableLogging = context.isDebuggableBuild(),
                    metricsSink = metricsSink.orElse(null),
                ),
            )
        return Downloader.getInstance(context, config, client, notifierOverride.orElse(null))
    }
}
