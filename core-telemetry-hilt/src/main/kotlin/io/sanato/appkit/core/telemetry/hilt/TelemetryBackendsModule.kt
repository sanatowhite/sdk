package io.sanato.appkit.core.telemetry.hilt

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import io.sanato.appkit.core.init.AppInitializer
import io.sanato.appkit.core.init.Deferred
import io.sanato.appkit.core.init.Eager
import io.sanato.appkit.core.telemetry.AnrCheckInitializer
import io.sanato.appkit.core.telemetry.CrashReportingInitializer
import io.sanato.appkit.core.telemetry.DiagnosticLogSink
import io.sanato.appkit.core.telemetry.MemorySamplerInitializer
import io.sanato.appkit.core.telemetry.StartupTrackerInitializer
import io.sanato.appkit.core.telemetry.Telemetry

/**
 * `Set<Telemetry>` 空集在 Hilt 里默认是编译错误——即便目前总有
 * [TelemetryModule.provideLogcatOrNoOpTelemetry] 这一条 `@IntoSet` 兜底,这条
 * `@Multibinds` 仍然保留:哪天有人把那条兜底删掉,这里不会突然编译失败,而是
 * 安全地退化成空集。
 *
 * 四个 `@Binds @IntoSet ... AppInitializer` 贡献的是 `:core-telemetry` 里
 * 那四个 initializer 实现——`:core-init-hilt` 的 `AppInitializerModule` 只有
 * `@Multibinds` 声明,具体绑定在这里,Hilt 允许多个 `@Module` 往同一个
 * multibinding 贡献元素。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryBackendsModule {
    @Multibinds
    abstract fun telemetryBackends(): Set<Telemetry>

    /**
     * `AnrCheckInitializer` 无条件绑进下面的 `Set<AppInitializer>`，因此它需要的
     * `DiagnosticLogSink` 对任何只引了这个模块（没有像 `:app` 的 `LogKitModule`
     * 那样另外提供桥接实现）的消费方来说必须有默认值，否则会在 `hiltJavaCompile`
     * 直接 MissingBinding。这条声明只是把 `Optional<DiagnosticLogSink>` 变成可注入
     * ——存在真实绑定就是 `Optional.of(...)`，不存在就是 `Optional.empty()`
     * （`AnrCheckInitializer` 自己再 `.orElse(DiagnosticLogSink.NoOp)`），不会跟
     * 任何下游提供的裸 `DiagnosticLogSink` 绑定冲突。
     */
    @BindsOptionalOf
    abstract fun diagnosticLogSink(): DiagnosticLogSink

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
