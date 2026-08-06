package io.sanato.apptemplate.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.apptemplate.core.data.ThemeMode
import io.sanato.apptemplate.core.data.UserSettings
import io.sanato.apptemplate.core.data.UserSettingsRepository
import io.sanato.logkit.LogKit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val userSettingsRepository: UserSettingsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<UserSettings> =
            userSettingsRepository.settings.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                UserSettings(),
            )

        // 只记设置项的值,绝不记内容本身——这几个字段本来就是有限枚举/布尔值,
        // 没有 PII 可泄露,是埋点里最安全的一类。

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { userSettingsRepository.setThemeMode(mode) }
            LogKit.i("Settings", "setting themeMode=$mode")
        }

        fun setDynamicColorEnabled(enabled: Boolean) {
            viewModelScope.launch { userSettingsRepository.setDynamicColorEnabled(enabled) }
            LogKit.i("Settings", "setting dynamicColor=$enabled")
        }

        fun setNotificationsEnabled(enabled: Boolean) {
            viewModelScope.launch { userSettingsRepository.setNotificationsEnabled(enabled) }
            LogKit.i("Settings", "setting notifications=$enabled")
        }

        fun setTelemetryEnabled(enabled: Boolean) {
            viewModelScope.launch { userSettingsRepository.setTelemetryEnabled(enabled) }
            LogKit.i("Settings", "setting telemetry=$enabled")
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
