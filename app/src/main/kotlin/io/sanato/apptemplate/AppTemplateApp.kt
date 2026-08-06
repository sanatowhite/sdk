package io.sanato.apptemplate

import dagger.hilt.android.HiltAndroidApp
import io.sanato.appkit.core.telemetry.hilt.TelemetryApplication

/**
 * 启动编排（Eager/Deferred 跑批、首帧后调度）+ 崩溃 handler 安装 + 启动计时
 * 全部由 `:core-telemetry-hilt` 的 [TelemetryApplication] 提供——一行接入。
 */
@HiltAndroidApp
class AppTemplateApp : TelemetryApplication()
