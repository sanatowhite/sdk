package io.sanato.apptemplate.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.apptemplate.core.data.UserSettingsRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConsentViewModel
    @Inject
    constructor(
        private val userSettingsRepository: UserSettingsRepository,
    ) : ViewModel() {
        fun accept(onAccepted: () -> Unit) {
            viewModelScope.launch {
                userSettingsRepository.setConsentVersion(CURRENT_CONSENT_VERSION)
                onAccepted()
            }
        }
    }
