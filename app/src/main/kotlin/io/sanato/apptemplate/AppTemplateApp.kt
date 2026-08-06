package io.sanato.apptemplate

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewTreeObserver
import dagger.hilt.android.HiltAndroidApp
import io.sanato.apptemplate.core.telemetry.crash.CrashRecorder
import io.sanato.apptemplate.core.telemetry.startup.AppStartTime
import io.sanato.apptemplate.init.AppInitializers
import io.sanato.apptemplate.logging.LogKitDiagnosticSink
import io.sanato.apptemplate.logging.LogKitInstall
import io.sanato.logkit.LogKit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltAndroidApp
class AppTemplateApp : Application() {
    @Inject
    lateinit var appInitializers: AppInitializers

    /**
     * 崩溃 handler、启动计时、日志落盘是唯三必须在 `attachBaseContext` 里做的事——
     * 比 Hilt 组装更早,也是 `AppInitializers` 那套 Eager/Deferred 分组机制
     * 覆盖不到的例外。
     *
     * 顺序有意义:
     *  1. `AppStartTime.record` 仍然第一——它采样进程 importance,越早越真;
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
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        AppStartTime.record(this)
        LogKitInstall.install(this)
        CrashRecorder.install(this, LogKitDiagnosticSink)
        LogKit.i(
            "App",
            "attachBaseContext pid=${android.os.Process.myPid()} versionName=${BuildConfig.VERSION_NAME} sha=${BuildConfig.GIT_SHA}",
        )
    }

    override fun onCreate() {
        super.onCreate()
        LogKit.i("App", "onCreate: runEager begin")
        appInitializers.runEager(this)
        LogKit.i("App", "onCreate: runEager end")
        scheduleDeferredInitAfterFirstFrame()
    }

    /**
     * "首帧后"用第一个 Activity 窗口的 draw 回调判定,不是简单在 onCreate 里
     * postDelayed 一个固定时间——那样要么太早(首帧还没画完)要么太晚(瞎猜的
     * 延迟)。摘监听统一走 `Handler.post`(而不是在 onDraw 内直接
     * removeOnDrawListener),避免低版本上的时序问题。`hasScheduled` 防止
     * 摘除完成前的多次 onDraw 回调重复触发 deferred 初始化。
     */
    private fun scheduleDeferredInitAfterFirstFrame() {
        val handler = Handler(Looper.getMainLooper())
        val hasScheduled = AtomicBoolean(false)

        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    val decorView = activity.window.decorView
                    val listener =
                        object : ViewTreeObserver.OnDrawListener {
                            override fun onDraw() {
                                if (hasScheduled.compareAndSet(false, true)) {
                                    handler.post {
                                        LogKit.i("App", "onCreate: runDeferred begin")
                                        appInitializers.runDeferred(this@AppTemplateApp)
                                        LogKit.i("App", "onCreate: runDeferred end")
                                    }
                                }
                                handler.post {
                                    if (decorView.viewTreeObserver.isAlive) {
                                        decorView.viewTreeObserver.removeOnDrawListener(this)
                                    }
                                }
                            }
                        }
                    decorView.viewTreeObserver.addOnDrawListener(listener)
                    unregisterActivityLifecycleCallbacks(this)
                }

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
