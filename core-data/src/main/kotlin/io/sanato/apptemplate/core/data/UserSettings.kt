package io.sanato.apptemplate.core.data

enum class ThemeMode { SYSTEM, LIGHT, DARK }

// 语言不进这里——由 AppCompatDelegate 托管(Phase 7),不是 DataStore 的职责。
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val telemetryEnabled: Boolean = true,
    val consentVersion: Int = 0,
)
