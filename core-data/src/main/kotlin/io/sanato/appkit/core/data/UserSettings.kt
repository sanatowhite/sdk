package io.sanato.appkit.core.data

enum class ThemeMode { SYSTEM, LIGHT, DARK }

// 语言不进这里——由 AppCompatDelegate 托管(Phase 7),不是 DataStore 的职责。
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val telemetryEnabled: Boolean = true,
    val consentVersion: Int = 0,
    // 全新安装也要写这个值(见 :app What's New 的接入点),否则首次启动就弹更新日志。
    val lastSeenWhatsNewVersionCode: Int = 0,
)
