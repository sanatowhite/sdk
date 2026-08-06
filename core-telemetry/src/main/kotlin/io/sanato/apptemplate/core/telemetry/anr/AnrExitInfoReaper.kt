package io.sanato.apptemplate.core.telemetry.anr

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * API 30+ 用 `ApplicationExitInfo` 开机回捞,不需要看门狗或信标——系统自己记录了
 * 退出原因。去重用"上次观测 key 全集的覆盖式持久化":每次读取后,把【当前】
 * 系统返回的历史记录 key 全集整体覆盖写回,而不是不断往一个 set 里追加——
 * 系统本身只保留有限条历史,覆盖式持久化让本地存储量同样有界。
 *
 * key = timestamp+pid+reason+processName——不用纯 pid(会被系统复用),
 * 不用时间戳高水位(设备时钟可能倒退,高水位比较会漏报)。
 */
@RequiresApi(Build.VERSION_CODES.R)
class AnrExitInfoReaper(
    private val context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun reapNewAnrExits(): List<ApplicationExitInfo> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val history = activityManager.getHistoricalProcessExitReasons(null, 0, 0)
        val anrs = history.filter { it.reason == ApplicationExitInfo.REASON_ANR }

        val seenKeys = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty()
        val currentKeys = anrs.map(::keyOf).toSet()
        val newOnes = anrs.filter { keyOf(it) !in seenKeys }

        prefs.edit().putStringSet(KEY_SEEN, currentKeys).apply()
        return newOnes
    }

    private fun keyOf(info: ApplicationExitInfo): String =
        "${info.timestamp}:${info.pid}:${info.reason}:${info.processName}"

    private companion object {
        const val PREFS_NAME = "telemetry_anr_reaper"
        const val KEY_SEEN = "seen_anr_keys"
    }
}
