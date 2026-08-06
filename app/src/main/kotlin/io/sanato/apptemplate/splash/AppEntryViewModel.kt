package io.sanato.apptemplate.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.apptemplate.consent.CURRENT_CONSENT_VERSION
import io.sanato.apptemplate.core.data.UserSettingsRepository
import io.sanato.apptemplate.navigation.Consent
import io.sanato.apptemplate.navigation.Home
import io.sanato.logkit.LogKit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val SPLASH_TIMEOUT_MILLIS = 1_500L

/**
 * 决定 `AppNavHost` 的 startDestination 是 [Home] 还是 [Consent]。带
 * [SPLASH_TIMEOUT_MILLIS] 超时——DataStore 读取异常/卡住不应该让启动画面永久
 * 挂住;超时时保守地要求走一遍同意页(合规优先于体验)。
 */
@HiltViewModel
class AppEntryViewModel
    @Inject
    constructor(
        userSettingsRepository: UserSettingsRepository,
    ) : ViewModel() {
        val startDestination: StateFlow<Any?> =
            flow {
                val settings = withTimeoutOrNull(SPLASH_TIMEOUT_MILLIS) { userSettingsRepository.settings.first() }
                if (settings == null) {
                    LogKit.w("Splash", "settings read timed out after ${SPLASH_TIMEOUT_MILLIS}ms — forcing Consent")
                }
                val destination =
                    if (settings != null && settings.consentVersion >= CURRENT_CONSENT_VERSION) Home else Consent
                LogKit.i("Splash", "startDestination = ${destination.javaClass.simpleName}")
                emit(destination)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }
