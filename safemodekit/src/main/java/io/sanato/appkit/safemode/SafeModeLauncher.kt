package io.sanato.appkit.safemode

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 切换桌面入口。alias 类名来自 [SafeModeConfig]，包名用运行时 packageName，
 * 这样带 applicationIdSuffix 的 debug 包也能正确定位组件。
 */
internal class SafeModeLauncher(
    private val realAlias: String,
    private val decoyAlias: String,
) {
    /** 先启用目标入口、再禁用另一个，保证任何时刻至少有一个桌面入口。 */
    fun apply(
        context: Context,
        decoy: Boolean,
    ) {
        val (on, off) = if (decoy) decoyAlias to realAlias else realAlias to decoyAlias
        setAlias(context, on, true)
        setAlias(context, off, false)
    }

    /** 伪装入口当前是否启用（alias 在 manifest 里默认禁用，DEFAULT 即禁用）。 */
    fun isDecoyActive(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(ComponentName(context.packageName, decoyAlias)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private fun setAlias(
        context: Context,
        alias: String,
        enabled: Boolean,
    ) {
        val state =
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context.packageName, alias),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }
}
