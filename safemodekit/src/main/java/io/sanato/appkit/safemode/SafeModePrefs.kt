package io.sanato.appkit.safemode

import android.content.Context

/**
 * 开关、暗号时长、退后台上锁宽限期、锁屏上锁。独立 prefs 文件：读取方是 Application 生命周期回调和 [DecoyActivity]，
 * 不依赖宿主的任何存储/DI。
 */
internal object SafeModePrefs {
    private const val FILE = "io.sanato.appkit.safemode"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOLD_MS = "hold_ms"
    private const val KEY_GRACE_MS = "grace_ms"
    private const val KEY_LOCK_ON_SCREEN_OFF = "lock_on_screen_off"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        // commit 而非 apply：开启后可能紧接着杀进程（killProcessOnEnable），异步写入会丢
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit()
    }

    fun holdMs(context: Context): Long =
        SafeModeSettings.coerceHoldMs(prefs(context).getLong(KEY_HOLD_MS, SafeModeSettings.DEFAULT_HOLD_MS))

    fun setHoldMs(
        context: Context,
        ms: Long,
    ) {
        prefs(context).edit().putLong(KEY_HOLD_MS, SafeModeSettings.coerceHoldMs(ms)).apply()
    }

    fun graceMs(context: Context): Long =
        SafeModeSettings.coerceGraceMs(prefs(context).getLong(KEY_GRACE_MS, SafeModeSettings.DEFAULT_GRACE_MS))

    fun setGraceMs(
        context: Context,
        ms: Long,
    ) {
        prefs(context).edit().putLong(KEY_GRACE_MS, SafeModeSettings.coerceGraceMs(ms)).apply()
    }

    fun lockOnScreenOff(context: Context): Boolean = prefs(context).getBoolean(KEY_LOCK_ON_SCREEN_OFF, true)

    fun setLockOnScreenOff(
        context: Context,
        enabled: Boolean,
    ) {
        prefs(context).edit().putBoolean(KEY_LOCK_ON_SCREEN_OFF, enabled).apply()
    }
}
