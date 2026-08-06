package io.sanato.apptemplate.telemetry

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import io.sanato.apptemplate.BuildConfig
import io.sanato.apptemplate.core.net.NetworkMetricsSink
import io.sanato.apptemplate.core.telemetry.AppForegroundState
import io.sanato.apptemplate.core.telemetry.CompositeTelemetry
import io.sanato.apptemplate.core.telemetry.LogcatTelemetry
import io.sanato.apptemplate.core.telemetry.NetworkRequestReport
import io.sanato.apptemplate.core.telemetry.NoOpTelemetry
import io.sanato.apptemplate.core.telemetry.RingLogBuffer
import io.sanato.apptemplate.core.telemetry.Telemetry
import io.sanato.apptemplate.core.telemetry.memory.MemorySampler
import javax.inject.Singleton

/**
 * `Set<Telemetry>` 空集在 Hilt 里默认是编译错误——即便目前(Firebase 关闭时)
 * 总有 [provideLogcatOrNoOpTelemetry] 这一条 `@IntoSet` 兜底,这条 `@Multibinds`
 * 仍然保留:哪天有人把那条兜底删掉,这里不会突然编译失败,而是安全地退化成空集。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryBackendsModule {
    @Multibinds
    abstract fun telemetryBackends(): Set<Telemetry>
}

@Module
@InstallIn(SingletonComponent::class)
object TelemetryModule {
    @Provides
    @IntoSet
    fun provideLogcatOrNoOpTelemetry(): Telemetry = if (BuildConfig.DEBUG) LogcatTelemetry() else NoOpTelemetry

    /**
     * `RingLogBuffer` 既是 Telemetry 后端(自动捕获所有事件)又要作为单例暴露给
     * 反馈页直接读 `snapshot()`——同一个实例通过两条绑定路径分别注入。
     */
    @Provides
    @Singleton
    fun provideRingLogBuffer(): RingLogBuffer = RingLogBuffer()

    @Provides
    @IntoSet
    fun provideRingLogBufferAsTelemetry(ringLogBuffer: RingLogBuffer): Telemetry = ringLogBuffer

    @Provides
    @Singleton
    fun provideTelemetry(backends: Set<@JvmSuppressWildcards Telemetry>): Telemetry = CompositeTelemetry(backends)

    @Provides
    @Singleton
    fun provideAppForegroundState(application: Application): AppForegroundState = AppForegroundState(application)

    /**
     * Singleton(不是每次 init() 现建)——`MainActivity` 也需要同一个实例,才能在
     * 冷启动完成时调用 [MemorySampler.sampleOnColdStartComplete]。
     */
    @Provides
    @Singleton
    fun provideMemorySampler(
        application: Application,
        foregroundState: AppForegroundState,
        telemetry: Telemetry,
    ): MemorySampler = MemorySampler(application, foregroundState, telemetry)

    /**
     * `NetworkMetricsSink` 接口归 `:core-net` 所有,这里只是桥接实现,把网络层
     * 的耗时数据转发进 `Telemetry`——`core-net` 与 `core-telemetry` 之间保持
     * 零依赖边,粘合只发生在 `:app`。
     */
    @Provides
    @Singleton
    fun provideNetworkMetricsSink(telemetry: Telemetry): NetworkMetricsSink =
        NetworkMetricsSink { routeTemplate, method, httpStatus, totalMillis, failed ->
            telemetry.networkRequest(
                NetworkRequestReport(
                    routeTemplate = routeTemplate,
                    method = method,
                    httpStatus = httpStatus,
                    totalMillis = totalMillis,
                    failed = failed,
                ),
            )
        }
}
