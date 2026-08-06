package io.sanato.appkit.core.data

import kotlinx.coroutines.flow.Flow

interface UserSettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColorEnabled(enabled: Boolean)

    suspend fun setNotificationsEnabled(enabled: Boolean)

    suspend fun setTelemetryEnabled(enabled: Boolean)

    suspend fun setConsentVersion(version: Int)

    suspend fun setLastSeenWhatsNewVersionCode(versionCode: Int)
}
