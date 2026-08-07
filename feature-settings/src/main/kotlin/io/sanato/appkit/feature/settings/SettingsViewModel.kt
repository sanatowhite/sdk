package io.sanato.appkit.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.appkit.core.data.ThemeMode
import io.sanato.appkit.core.data.UserSettings
import io.sanato.appkit.core.data.UserSettingsRepository
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

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { userSettingsRepository.setThemeMode(mode) }
        }

        fun setDynamicColorEnabled(enabled: Boolean) {
            viewModelScope.launch { userSettingsRepository.setDynamicColorEnabled(enabled) }
        }

        fun setNotificationsEnabled(enabled: Boolean) {
            viewModelScope.launch { userSettingsRepository.setNotificationsEnabled(enabled) }
        }

        fun setTelemetryEnabled(enabled: Boolean) {
            viewModelScope.launch { userSettingsRepository.setTelemetryEnabled(enabled) }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
