package io.sanato.appkit.core.telemetry.hilt

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.sanato.appkit.core.common.isDebuggableBuild
import io.sanato.appkit.core.telemetry.AppForegroundState
import io.sanato.appkit.core.telemetry.CompositeTelemetry
import io.sanato.appkit.core.telemetry.LogcatTelemetry
import io.sanato.appkit.core.telemetry.NoOpTelemetry
import io.sanato.appkit.core.telemetry.RingLogBuffer
import io.sanato.appkit.core.telemetry.Telemetry
import io.sanato.appkit.core.telemetry.memory.MemorySampler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TelemetryModule {
    // BuildConfig.DEBUG → application.isDebuggableBuild()：库模块拿不到消费方
    // 的 BuildConfig，读 ApplicationInfo.FLAG_DEBUGGABLE 语义上更准确，且三个
    // 默认 buildType（debug/release/staging）下行为和原来完全一致。
    @Provides
    @IntoSet
    fun provideLogcatOrNoOpTelemetry(application: Application): Telemetry =
        if (application.isDebuggableBuild()) LogcatTelemetry() else NoOpTelemetry

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
     * Singleton(不是每次 init() 现建)——消费方的 Activity 也需要同一个实例,
     * 才能在冷启动完成时调用 [MemorySampler.sampleOnColdStartComplete]。
     */
    @Provides
    @Singleton
    fun provideMemorySampler(
        application: Application,
        foregroundState: AppForegroundState,
        telemetry: Telemetry,
    ): MemorySampler = MemorySampler(application, foregroundState, telemetry)
}
