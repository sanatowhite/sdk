package io.sanato.appkit.feature.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.appkit.core.auth.AuthError
import io.sanato.appkit.core.auth.AuthRepository
import io.sanato.appkit.core.auth.PhoneAuthEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhoneNumberUiState(
    /**
     * ⚠️ Must already be E.164 (`"+14155552671"`) — this screen doesn't build a
     * country-code picker; it's a deliberate v1 simplification (a real product
     * would want one, but it's a UI concern orthogonal to the auth plumbing
     * this module exists to provide).
     */
    val phoneNumber: String = "",
    val inProgress: Boolean = false,
    val error: AuthError? = null,
) {
    val submitEnabled: Boolean get() =
        phoneNumber.startsWith("+") && phoneNumber.length > MIN_E164_LENGTH &&
            !inProgress

    private companion object {
        const val MIN_E164_LENGTH = 8
    }
}

sealed interface PhoneNumberEvent {
    data class CodeSent(
        val verificationId: String,
        val phoneNumberMasked: String,
    ) : PhoneNumberEvent

    /** Same-device instant verification completed before the user ever saw a code screen. */
    data object SignedIn : PhoneNumberEvent
}

@HiltViewModel
class PhoneNumberViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val form = MutableStateFlow(PhoneNumberUiState())
        val uiState: StateFlow<PhoneNumberUiState> = form.asStateFlow()

        private val eventChannel = Channel<PhoneNumberEvent>(Channel.BUFFERED)
        val events: Flow<PhoneNumberEvent> = eventChannel.receiveAsFlow()

        fun onPhoneNumberChange(value: String) {
            form.update { it.copy(phoneNumber = value, error = null) }
        }

        fun sendCode(activity: Activity) {
            val current = form.value
            if (!current.submitEnabled) return
            viewModelScope.launch {
                form.update { it.copy(inProgress = true) }
                authRepository.signInWithPhoneNumber(activity, current.phoneNumber).collect { event ->
                    when (event) {
                        is PhoneAuthEvent.CodeSent -> {
                            form.update { it.copy(inProgress = false) }
                            eventChannel.send(
                                PhoneNumberEvent.CodeSent(
                                    event.verificationId.value,
                                    maskPhoneNumber(current.phoneNumber),
                                ),
                            )
                        }

                        is PhoneAuthEvent.AutoRetrieved -> {
                            form.update { it.copy(inProgress = false) }
                            eventChannel.send(PhoneNumberEvent.SignedIn)
                        }

                        // Only relevant once on the code screen.
                        PhoneAuthEvent.AutoRetrievalTimeout -> {}

                        is PhoneAuthEvent.Failed -> {
                            form.update { it.copy(inProgress = false, error = event.error) }
                        }
                    }
                }
            }
        }
    }

/** `"+14155552671"` → `"+1 ••• ••• 2671"`-ish — keeps the country code and last 4 digits, masks the rest. */
internal fun maskPhoneNumber(e164: String): String {
    if (e164.length <= MASK_VISIBLE_SUFFIX_LENGTH + 1) return e164
    val visibleSuffix = e164.takeLast(MASK_VISIBLE_SUFFIX_LENGTH)
    val maskedLength = e164.length - MASK_VISIBLE_SUFFIX_LENGTH - 1
    return "+" + "•".repeat(maskedLength.coerceAtLeast(0)) + visibleSuffix
}

private const val MASK_VISIBLE_SUFFIX_LENGTH = 4
