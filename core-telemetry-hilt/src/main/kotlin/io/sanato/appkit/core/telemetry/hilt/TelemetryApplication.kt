package io.sanato.appkit.core.telemetry.hilt

import android.app.Application
import io.sanato.appkit.core.init.hilt.HiltInitializingApplication
import io.sanato.appkit.core.telemetry.crash.CrashRecorder
import io.sanato.appkit.core.telemetry.startup.AppStartTime

/**
 * 消费方 Hilt Application 的基类——一行接入：
 *
 * ```
 * @HiltAndroidApp
 * class MyApp : TelemetryApplication()
 * ```
 *
 * 崩溃 handler 安装 + 启动计时记录是唯二必须早于 Hilt 组装完成的事，覆盖
 * [HiltInitializingApplication.onPreDiSetup] 这个 hook 来做，而不是自己写
 * `attachBaseContext`。消费方需要在这之上再加自己的初始化时，记得调用
 * `super.onPreDiSetup(application)`，否则会丢掉这两行。
 */
abstract class TelemetryApplication : HiltInitializingApplication() {
    override fun onPreDiSetup(application: Application) {
        AppStartTime.record(application)
        CrashRecorder.install(application)
    }
}
