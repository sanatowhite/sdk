package io.sanato.appkit.core.telemetry

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/**
 * 前台/后台状态的唯一真源——用 started-Activity 计数而不是 resumed/paused:
 * 多 Activity 叠加(比如透明 Activity)场景下 resumed 状态在切换瞬间会出现
 * 短暂的"全部 paused"假后台,started 计数更稳定,这是 AndroidX
 * `ProcessLifecycleOwner` 内部同样采用的信号。
 */
class AppForegroundState(
    application: Application,
) {
    private val startedCount = AtomicInteger(0)
    private val listeners = mutableListOf<(Boolean) -> Unit>()

    val isForeground: Boolean get() = startedCount.get() > 0

    fun addListener(listener: (isForeground: Boolean) -> Unit) {
        listeners += listener
    }

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    if (startedCount.getAndIncrement() == 0) notifyListeners(isForeground = true)
                }

                override fun onActivityStopped(activity: Activity) {
                    if (startedCount.decrementAndGet() == 0) notifyListeners(isForeground = false)
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    private fun notifyListeners(isForeground: Boolean) {
        listeners.forEach { it(isForeground) }
    }
}
