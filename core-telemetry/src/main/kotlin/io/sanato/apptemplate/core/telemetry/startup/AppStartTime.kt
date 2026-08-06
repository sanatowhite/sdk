package io.sanato.apptemplate.core.telemetry.startup

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock

/**
 * 冷启动起点用 `Process.getStartUptimeMillis()`(API 24 起可用,无需 guard)——
 * 不是 `attachBaseContext`,那样会漏掉进程 fork 到 Application 之间的时间,
 * 而这段恰恰是 ContentProvider 初始化的重灾区。
 *
 * [record] 必须在 `attachBaseContext` 里尽早调用一次(比 DI 组装更早),同时
 * 顺手把"进程刚 fork 出来时是否已经是前台重要性"记下来,供后台启动否决使用——
 * 一旦真的有 Activity 开始创建,进程重要性几乎必然已经是前台,那时候再判断
 * 已经来不及了。
 */
object AppStartTime {
    @Volatile
    private var recordedAtUptimeMillis: Long? = null

    @Volatile
    private var startedAtBackgroundImportance: Boolean = false

    fun record(context: Context) {
        if (recordedAtUptimeMillis != null) return
        recordedAtUptimeMillis = Process.getStartUptimeMillis()
        startedAtBackgroundImportance = isBackgroundImportance(context)
    }

    fun elapsedSinceStartMillis(nowUptimeMillis: Long = SystemClock.uptimeMillis()): Long? =
        recordedAtUptimeMillis?.let { nowUptimeMillis - it }

    /** 冷启动 TTID 计算的参照点——`SystemClock.uptimeMillis()` 语义下的进程起点。 */
    fun referenceUptimeMillis(): Long? = recordedAtUptimeMillis

    /**
     * 进程刚 fork 时如果已经不是前台重要性,大概率是被 BroadcastReceiver/Service/
     * ContentProvider 拉起来做后台工作,用户之后才打开 Activity——这种情况下
     * "进程起点到首帧"的耗时统计会被无关的后台工作严重污染,应当否决上报。
     */
    fun wasLikelyBackgroundLaunch(): Boolean = startedAtBackgroundImportance

    private fun isBackgroundImportance(context: Context): Boolean {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val myPid = Process.myPid()
        val info =
            activityManager.runningAppProcesses?.firstOrNull { it.pid == myPid }
                ?: return false
        return info.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    /** 仅供测试重置单例状态。 */
    internal fun resetForTest() {
        recordedAtUptimeMillis = null
        startedAtBackgroundImportance = false
    }
}
