package io.sanato.appkit.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory fake for ViewModel/Repository 单测,不落盘,不依赖 Android Context。 */
class FakeUserSettingsRepository(
    initial: UserSettings = UserSettings(),
) : UserSettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings = state.asStateFlow()

    fun currentValue(): UserSettings = state.value

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.update { it.copy(themeMode = mode) }
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        state.update { it.copy(dynamicColorEnabled = enabled) }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        state.update { it.copy(notificationsEnabled = enabled) }
    }

    override suspend fun setTelemetryEnabled(enabled: Boolean) {
        state.update { it.copy(telemetryEnabled = enabled) }
    }

    override suspend fun setConsentVersion(version: Int) {
        state.update { it.copy(consentVersion = version) }
    }

    override suspend fun setLastSeenWhatsNewVersionCode(versionCode: Int) {
        state.update { it.copy(lastSeenWhatsNewVersionCode = versionCode) }
    }
}
