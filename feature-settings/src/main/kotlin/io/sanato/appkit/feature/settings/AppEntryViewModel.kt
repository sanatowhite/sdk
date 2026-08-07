package io.sanato.appkit.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.appkit.core.data.UserSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val SPLASH_TIMEOUT_MILLIS = 1_500L

/**
 * 决定消费方的 `NavHost` 该不该先走 [ConsentRoute]。带 [SPLASH_TIMEOUT_MILLIS]
 * 超时——DataStore 读取异常/卡住不应该让启动画面永久挂住;超时时保守地要求
 * 走一遍同意页(合规优先于体验)。
 *
 * 只暴露一个布尔信号,不是具体的 `startDestination`——"未同意时该去哪个非同意
 * 页"是消费方自己的路由图决定的,消费方在自己的入口 Composable 里写:
 *
 * ```
 * val consentRequired by appEntryViewModel.consentRequired.collectAsStateWithLifecycle()
 * consentRequired?.let { needsConsent ->
 *     AppNavHost(startDestination = if (needsConsent) ConsentRoute else Home)
 * }
 * ```
 */
@HiltViewModel
class AppEntryViewModel
    @Inject
    constructor(
        userSettingsRepository: UserSettingsRepository,
    ) : ViewModel() {
        /** `null` = 还不知道(等待中);`true` = 需要走同意页;`false` = 不需要。 */
        val consentRequired: StateFlow<Boolean?> =
            flow {
                val settings = withTimeoutOrNull(SPLASH_TIMEOUT_MILLIS) { userSettingsRepository.settings.first() }
                emit(settings == null || settings.consentVersion < CURRENT_CONSENT_VERSION)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }
