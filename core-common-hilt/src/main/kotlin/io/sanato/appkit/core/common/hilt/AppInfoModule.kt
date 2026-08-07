package io.sanato.appkit.core.common.hilt

import android.app.Application
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.sanato.appkit.core.common.AppBuildInfo
import java.util.Optional
import javax.inject.Singleton

/**
 * 消费方想要 `gitSha`/`buildTimeMillis`（自己 convention plugin 注进
 * `BuildConfig` 的那两项）时，提供这个可选绑定：
 *
 * ```
 * @Module @InstallIn(SingletonComponent::class)
 * object MyAppInfoOverrideModule {
 *     @Provides fun override() = AppBuildInfoOverride(BuildConfig.GIT_SHA, BuildConfig.BUILD_TIME_MILLIS)
 * }
 * ```
 *
 * 不提供就是零配置，`AppBuildInfo` 的这两个字段维持默认值。
 */
data class AppBuildInfoOverride(
    val gitSha: String,
    val buildTimeMillis: Long,
)

@Module
@InstallIn(SingletonComponent::class)
abstract class AppInfoOverrideModule {
    @BindsOptionalOf
    abstract fun appBuildInfoOverride(): AppBuildInfoOverride
}

@Module
@InstallIn(SingletonComponent::class)
object AppInfoModule {
    @Provides
    @Singleton
    fun provideAppBuildInfo(
        application: Application,
        override: Optional<AppBuildInfoOverride>,
    ): AppBuildInfo {
        val base = AppBuildInfo.fromPackageManager(application)
        return if (override.isPresent) {
            base.copy(gitSha = override.get().gitSha, buildTimeMillis = override.get().buildTimeMillis)
        } else {
            base
        }
    }
}
