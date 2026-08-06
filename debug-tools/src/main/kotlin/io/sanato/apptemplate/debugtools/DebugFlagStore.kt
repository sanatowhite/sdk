package io.sanato.apptemplate.debugtools

import android.content.Context
import android.content.SharedPreferences

/**
 * 简化版本地开关——本模板目前没有接入远程配置系统(计划里描述的
 * `RemoteJsonSource`/`FlagKey`/`AppFlags` 注册表是明显更大的一块能力,留给
 * 需要的项目自己按需加,这里只做"本地覆写、纯 debug 可见"这一层,演示
 * Debug Drawer 该有的交互模式)。
 */
class DebugFlagStore(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("debug_tools_flags", Context.MODE_PRIVATE)

    fun isEnabled(
        key: String,
        default: Boolean = false,
    ): Boolean = prefs.getBoolean(key, default)

    fun setEnabled(
        key: String,
        enabled: Boolean,
    ) {
        prefs.edit().putBoolean(key, enabled).apply()
    }

    companion object {
        val KNOWN_FLAGS = listOf("force_update_available", "simulate_offline")
    }
}
