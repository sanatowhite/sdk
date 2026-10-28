package io.sanato.appkit.safemode

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

/**
 * 安全模式门禁：退到后台/锁屏即上锁并 finish 所有真实页面，任务栈清空后从最近任务或桌面
 * 回来都会重新走 [DecoyActivity]。安全模式关闭或尚未 [install] 时不处理任何事件。
 */
internal object SafeModeGate {
    private var app: Application? = null
    var config: SafeModeConfig? = null
        private set
    var launcher: SafeModeLauncher? = null
        private set

    private val state = SafeModeLockState { SystemClock.elapsedRealtime() }
    private val activities = mutableListOf<WeakReference<Activity>>()
    private var startedCount = 0

    val isInstalled: Boolean get() = app != null

    private fun enabled(): Boolean = app?.let { SafeModePrefs.isEnabled(it) } ?: false

    fun install(
        application: Application,
        safeModeConfig: SafeModeConfig,
    ) {
        // 同一个 Application 重复调用直接忽略；换了 Application（Robolectric 每个用例新建一个，
        // 但 object 的静态状态会跨用例保留）则重新绑定，而不是抛异常
        if (app === application) return
        app = application
        config = safeModeConfig
        val appLauncher = SafeModeLauncher(safeModeConfig.realLauncherAlias, safeModeConfig.decoyLauncherAlias)
        launcher = appLauncher
        // prefs 和桌面入口状态分别存储（备份恢复、清数据可能只动其中一个），启动时以开关为准对齐
        if (appLauncher.isDecoyActive(application) != enabled()) {
            appLauncher.apply(application, enabled())
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        ContextCompat.registerReceiver(
            application,
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (enabled() && state.onScreenOff()) finishRealActivities()
                }
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun unlock() = state.unlock()

    /**
     * 立即上锁并关闭所有真实页面（效果同退后台），下次进入必须走伪装页。
     * [SafeModeConfig.killProcessOnEnable] 为 true 时再结束进程：先 finish 再延迟杀，避免系统把
     * 「进程死了但还在前台的页面」当成崩溃重新拉起。
     */
    fun lockNow() {
        state.lockNow()
        finishRealActivities()
        if (config?.killProcessOnEnable == true) {
            Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, KILL_DELAY_MS)
        }
    }

    private const val KILL_DELAY_MS = 300L

    fun allowExternalTrip() = state.allowExternalTrip()

    fun beginAuthentication() = state.beginAuthentication()

    fun endAuthentication() = state.endAuthentication()

    private fun finishRealActivities() {
        activities.mapNotNull { it.get() }.filter { it !is DecoyActivity }.forEach { it.finish() }
    }

    private fun redirectToDecoy(from: Activity) {
        from.startActivity(
            Intent(from, DecoyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        from.finish()
    }

    private val callbacks =
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: Bundle?,
            ) {
                activities.add(WeakReference(activity))
                // 锁定状态下从通知/深链/最近任务直达真实页面：拦回伪装页
                if (activity !is DecoyActivity && enabled() && state.isLocked) redirectToDecoy(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                startedCount++
                if (startedCount == 1 && enabled() && state.onAppForegrounded() && activity !is DecoyActivity) {
                    redirectToDecoy(activity)
                }
            }

            override fun onActivityResumed(activity: Activity) = state.onActivityResumed()

            override fun onActivityStopped(activity: Activity) {
                startedCount--
                // 旋转/夜间模式 recreate 不算退后台
                if (activity.isChangingConfigurations) return
                if (startedCount == 0 && enabled() && state.onAppBackgrounded()) finishRealActivities()
            }

            override fun onActivityDestroyed(activity: Activity) {
                activities.removeAll { it.get() == null || it.get() === activity }
            }

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivitySaveInstanceState(
                activity: Activity,
                outState: Bundle,
            ) {}
        }
}
