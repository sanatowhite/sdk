package io.sanato.apptemplate

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sanato.appkit.core.telemetry.crash.CrashRecorder
import io.sanato.appkit.core.telemetry.hilt.TelemetryApplication
import io.sanato.appkit.core.telemetry.startup.AppStartTime
import io.sanato.apptemplate.logging.LogKitDiagnosticSink
import io.sanato.apptemplate.logging.LogKitInstall
import io.sanato.logkit.LogKit

/**
 * 启动编排（Eager/Deferred 跑批、首帧后调度）由 [TelemetryApplication] 的父类
 * `HiltInitializingApplication` 提供。这里覆盖 [onPreDiSetup] 而不调用
 * `super.onPreDiSetup()`——顺序要求把 `LogKit.install()` 插在
 * `AppStartTime.record()` 和 `CrashRecorder.install()` 之间(见下面三步的
 * 注释),而 `super` 把这两步捆在一起,没有插入点。
 */
@HiltAndroidApp
class AppTemplateApp : TelemetryApplication() {
    /**
     * 顺序有意义:
     *  1. `AppStartTime.record` 第一——它采样进程 importance,越早越真;
     *     `LogKit.install()` 会做磁盘 IO(建目录/建首个文件),排它后面会污染
     *     被测量的那个窗口。
     *  2. `LogKit.install()` 必须在 `CrashRecorder.install()` 之前——虽然
     *     `LogKitDiagnosticSink` 是懒解析的 object,严格来说谁先谁后不影响
     *     崩溃路径本身,但这样排列让"日志先准备好,再装崩溃处理器"这个依赖
     *     方向读起来更直观。
     *  3. `LogKit` 绝不调用 `Thread.setDefaultUncaughtExceptionHandler`——
     *     `CrashRecorder` 是本仓库唯一的崩溃处理器,这是 `:logkit` 的设计
     *     铁律之一(它是管道不是探测器),不是这里手动维护的顺序保证。
     */
    override fun onPreDiSetup(application: Application) {
        AppStartTime.record(application)
        LogKitInstall.install(application)
        CrashRecorder.install(application, LogKitDiagnosticSink)
        LogKit.i(
            "App",
            "attachBaseContext pid=${android.os.Process.myPid()} versionName=${BuildConfig.VERSION_NAME} sha=${BuildConfig.GIT_SHA}",
        )
    }

    override fun onCreate() {
        LogKit.i("App", "onCreate begin")
        super.onCreate()
        LogKit.i("App", "onCreate end")
    }
}
