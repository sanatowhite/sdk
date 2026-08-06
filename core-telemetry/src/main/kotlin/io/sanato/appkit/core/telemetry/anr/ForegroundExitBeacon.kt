package io.sanato.appkit.core.telemetry.anr

import android.content.Context
import android.content.SharedPreferences

/**
 * API 24-29 没有 `ApplicationExitInfo`,用"非正常退出信标"做启发式检测:前台时
 * 写一个"仍在前台"标记,应用完全进入后台(所有 Activity 都 onStop)时清除。
 * 下次冷启动发现标记残留,说明上一次会话是在前台状态下被杀掉的——ANR/强杀/
 * OOM-killer 三者从这个信标区分不出来,但都值得上报关注。
 *
 * 明确不写看门狗线程:主线程轮询自身本身就有性能开销,而且自己可能成为
 * ANR 的一部分;这个信标方案零运行时开销,聚合统计交给 Play Vitals 已经够用。
 */
class ForegroundExitBeacon(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 在 Eager 初始化阶段调用一次;返回 true 表示上次会话疑似前台异常退出。 */
    fun consumePreviousSessionAbnormalExit(): Boolean {
        val wasInForeground = prefs.getBoolean(KEY_IN_FOREGROUND, false)
        prefs.edit().putBoolean(KEY_IN_FOREGROUND, false).apply()
        return wasInForeground
    }

    fun markForeground() {
        prefs.edit().putBoolean(KEY_IN_FOREGROUND, true).apply()
    }

    fun markBackground() {
        prefs.edit().putBoolean(KEY_IN_FOREGROUND, false).apply()
    }

    private companion object {
        const val PREFS_NAME = "telemetry_anr_beacon"
        const val KEY_IN_FOREGROUND = "in_foreground"
    }
}
