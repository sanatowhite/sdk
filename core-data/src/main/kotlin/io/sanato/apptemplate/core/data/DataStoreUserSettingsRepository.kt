package io.sanato.apptemplate.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreUserSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : UserSettingsRepository {
        override val settings: Flow<UserSettings> =
            dataStore.data.map { prefs ->
                UserSettings(
                    themeMode = prefs[Keys.THEME_MODE]?.let(::parseThemeMode) ?: ThemeMode.SYSTEM,
                    dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: true,
                    notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
                    telemetryEnabled = prefs[Keys.TELEMETRY] ?: true,
                    consentVersion = prefs[Keys.CONSENT_VERSION] ?: 0,
                    lastSeenWhatsNewVersionCode = prefs[Keys.LAST_SEEN_WHATS_NEW] ?: 0,
                )
            }

        override suspend fun setThemeMode(mode: ThemeMode) {
            dataStore.edit { it[Keys.THEME_MODE] = mode.name }
        }

        override suspend fun setDynamicColorEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
        }

        override suspend fun setNotificationsEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
        }

        override suspend fun setTelemetryEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.TELEMETRY] = enabled }
        }

        override suspend fun setConsentVersion(version: Int) {
            dataStore.edit { it[Keys.CONSENT_VERSION] = version }
        }

        override suspend fun setLastSeenWhatsNewVersionCode(versionCode: Int) {
            dataStore.edit { it[Keys.LAST_SEEN_WHATS_NEW] = versionCode }
        }

        private fun parseThemeMode(raw: String): ThemeMode =
            runCatching {
                ThemeMode.valueOf(raw)
            }.getOrDefault(ThemeMode.SYSTEM)

        private object Keys {
            val THEME_MODE = stringPreferencesKey("theme_mode")
            val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
            val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
            val TELEMETRY = booleanPreferencesKey("telemetry_enabled")
            val CONSENT_VERSION = intPreferencesKey("consent_version")
            val LAST_SEEN_WHATS_NEW = intPreferencesKey("last_seen_whats_new_version_code")
        }
    }
