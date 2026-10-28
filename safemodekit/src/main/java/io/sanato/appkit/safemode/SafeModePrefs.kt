package io.sanato.appkit.safemode

import android.content.Context

/**
 * 开关和暗号时长。独立 prefs 文件：读取方是 Application 生命周期回调和 [DecoyActivity]，
 * 不依赖宿主的任何存储/DI。
 */
internal object SafeModePrefs {
    private const val FILE = "io.sanato.appkit.safemode"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOLD_MS = "hold_ms"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun holdMs(context: Context): Long =
        SafeModeSettings.coerceHoldMs(prefs(context).getLong(KEY_HOLD_MS, SafeModeSettings.DEFAULT_HOLD_MS))

    fun setHoldMs(
        context: Context,
        ms: Long,
    ) {
        prefs(context).edit().putLong(KEY_HOLD_MS, SafeModeSettings.coerceHoldMs(ms)).apply()
    }
}
