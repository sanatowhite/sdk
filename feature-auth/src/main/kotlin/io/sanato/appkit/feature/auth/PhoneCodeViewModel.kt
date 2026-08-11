package io.sanato.appkit.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.appkit.core.auth.AuthError
import io.sanato.appkit.core.auth.AuthRepository
import io.sanato.appkit.core.auth.PhoneVerificationId
import io.sanato.appkit.core.common.AppResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhoneCodeUiState(
    val phoneNumberMasked: String = "",
    val code: String = "",
    val codeLength: Int = CODE_LENGTH,
    val verifying: Boolean = false,
    /** 0 ⇒ resend is available. Computed by the ViewModel, never by the Screen ticking its own timer — see [PhoneCodeScreen]. */
    val resendSecondsRemaining: Int = RESEND_COOLDOWN_SECONDS,
    val error: AuthError? = null,
) {
    private companion object {
        const val CODE_LENGTH = 6
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}

sealed interface PhoneCodeEvent {
    data object Verified : PhoneCodeEvent
}

@HiltViewModel
class PhoneCodeViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<PhoneCodeRoute>()

        private val form = MutableStateFlow(PhoneCodeUiState(phoneNumberMasked = route.phoneNumberMasked))
        val uiState: StateFlow<PhoneCodeUiState> = form.asStateFlow()

        private val eventChannel = Channel<PhoneCodeEvent>(Channel.BUFFERED)
        val events: Flow<PhoneCodeEvent> = eventChannel.receiveAsFlow()

        init {
            startResendCountdown()
        }

        fun onCodeChange(value: String) {
            val digitsOnly = value.filter { it.isDigit() }.take(form.value.codeLength)
            form.update { it.copy(code = digitsOnly, error = null) }
            if (digitsOnly.length == form.value.codeLength) submit()
        }

        fun submit() {
            val current = form.value
            if (current.code.length != current.codeLength || current.verifying) return
            viewModelScope.launch {
                form.update { it.copy(verifying = true) }
                val result =
                    authRepository.confirmPhoneVerificationCode(
                        PhoneVerificationId(route.verificationId),
                        current.code,
                    )
                when (result) {
                    is AppResult.Success -> {
                        eventChannel.send(PhoneCodeEvent.Verified)
                    }

                    is AppResult.Failure -> {
                        val error = result.error as? AuthError ?: AuthError.Unknown(null, result.error)
                        // Clear the code, not the whole form — the user shouldn't have to retype the phone number.
                        form.update { it.copy(verifying = false, error = error, code = "") }
                    }
                }
            }
        }

        private fun startResendCountdown() {
            viewModelScope.launch {
                for (remaining in form.value.resendSecondsRemaining downTo 0) {
                    form.update { it.copy(resendSecondsRemaining = remaining) }
                    delay(1_000)
                }
            }
        }
    }
