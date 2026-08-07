package io.sanato.appkit.core.telemetry.startup

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.ViewTreeObserver
import io.sanato.appkit.core.telemetry.LaunchType
import io.sanato.appkit.core.telemetry.StartupReport
import io.sanato.appkit.core.telemetry.Telemetry
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 进程内第一个 Activity.onCreate = COLD,同一进程后续的 onCreate(比如内存不足时
 * Activity 被回收、用户返回时重建)= WARM。加两道过滤:
 * - 非主进程否决(多进程组件不该污染启动指标)
 * - 后台启动否决(仅对 COLD 生效——进程如果是被 BroadcastReceiver/Service/
 *   ContentProvider 拉起来做后台工作,用户之后才打开 Activity,这次统计出的
 *   耗时会被无关的后台工作严重污染)
 *
 * 首帧(TTID)用 `ViewTreeObserver.OnDrawListener`,一律在 `Handler.post` 里摘
 * 监听(而不是在 onDraw 内直接 remove)。
 */
class StartupTracker(
    private val application: Application,
    private val telemetry: Telemetry,
) {
    @Volatile
    private var hasCreatedAnyActivity = false

    fun start() {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    val launchType = if (hasCreatedAnyActivity) LaunchType.WARM else LaunchType.COLD
                    val activityCreateUptimeMillis = SystemClock.uptimeMillis()
                    hasCreatedAnyActivity = true

                    if (!isMainProcess(activity)) return
                    if (launchType == LaunchType.COLD && AppStartTime.wasLikelyBackgroundLaunch()) return

                    val referenceUptimeMillis =
                        if (launchType == LaunchType.COLD) {
                            AppStartTime.referenceUptimeMillis() ?: activityCreateUptimeMillis
                        } else {
                            activityCreateUptimeMillis
                        }

                    reportFirstDraw(activity, launchType, referenceUptimeMillis)
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

    private fun reportFirstDraw(
        activity: Activity,
        launchType: LaunchType,
        referenceUptimeMillis: Long,
    ) {
        val decorView = activity.window.decorView
        val handler = Handler(Looper.getMainLooper())
        val hasReported = AtomicBoolean(false)

        val listener =
            object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    if (hasReported.compareAndSet(false, true)) {
                        val ttidMillis = SystemClock.uptimeMillis() - referenceUptimeMillis
                        handler.post {
                            telemetry.startup(
                                StartupReport(
                                    launchType = launchType,
                                    timeToInitialDisplayMillis = ttidMillis,
                                ),
                            )
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
    }

    private fun isMainProcess(context: Context): Boolean {
        val processName =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val pid = Process.myPid()
                activityManager?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
            }
        return processName == null || processName == context.packageName
    }
}
