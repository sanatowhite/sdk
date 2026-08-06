package io.sanato.apptemplate.init

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import io.sanato.apptemplate.telemetry.AnrCheckInitializer
import io.sanato.apptemplate.telemetry.CrashReportingInitializer
import io.sanato.apptemplate.telemetry.MemorySamplerInitializer
import io.sanato.apptemplate.telemetry.StartupTrackerInitializer

/**
 * `@Multibinds` 声明本身不依赖是否存在具体绑定——即使下面已经有真正的 `@Binds`
 * 贡献者,保留这两行仍然安全:哪天所有具体绑定都被移除,Hilt 也会安全地退化成
 * 空集而不是编译报错。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InitializerModule {
    @Multibinds
    @Eager
    abstract fun eagerInitializers(): Set<AppInitializer>

    @Multibinds
    @Deferred
    abstract fun deferredInitializers(): Set<AppInitializer>

    @Binds
    @IntoSet
    @Eager
    abstract fun bindStartupTrackerInitializer(impl: StartupTrackerInitializer): AppInitializer

    @Binds
    @IntoSet
    @Eager
    abstract fun bindAnrCheckInitializer(impl: AnrCheckInitializer): AppInitializer

    @Binds
    @IntoSet
    @Deferred
    abstract fun bindCrashReportingInitializer(impl: CrashReportingInitializer): AppInitializer

    @Binds
    @IntoSet
    @Deferred
    abstract fun bindMemorySamplerInitializer(impl: MemorySamplerInitializer): AppInitializer
}
