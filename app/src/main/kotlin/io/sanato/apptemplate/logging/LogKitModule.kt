package io.sanato.apptemplate.logging

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.sanato.apptemplate.core.telemetry.DiagnosticLogSink
import io.sanato.apptemplate.core.telemetry.Telemetry
import javax.inject.Singleton

/**
 * `:logkit` 接线,独立文件而不是塞进 `TelemetryModule.kt`——遵循"一个模块的
 * wiring 一份文件"的既有约定(参见 `InitializerModule.kt`/`TelemetryModule.kt`
 * 各自只管一件事)。
 */
@Module
@InstallIn(SingletonComponent::class)
object LogKitModule {
    @Provides
    @Singleton
    fun provideDiagnosticLogSink(): DiagnosticLogSink = LogKitDiagnosticSink

    /** LogKit 作为 Telemetry 后端接入——所有 startup/jank/network/crash/anr/screenView 事件自动进日志。 */
    @Provides
    @IntoSet
    fun provideLogKitTelemetry(): Telemetry = LogKitTelemetry()
}
