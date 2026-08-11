package io.sanato.appkit.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.appkit.core.auth.AuthError
import io.sanato.appkit.core.auth.AuthRepository
import io.sanato.appkit.core.common.AppResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignUpUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val inProgress: Boolean = false,
    val error: AuthError? = null,
) {
    val passwordsMatch: Boolean get() = password == confirmPassword
    val submitEnabled: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && passwordsMatch && !inProgress
}

sealed interface SignUpEvent {
    data object SignedUp : SignUpEvent
}

@HiltViewModel
class SignUpViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val form = MutableStateFlow(SignUpUiState())
        val uiState: StateFlow<SignUpUiState> = form.asStateFlow()

        private val eventChannel = Channel<SignUpEvent>(Channel.BUFFERED)
        val events: Flow<SignUpEvent> = eventChannel.receiveAsFlow()

        fun onEmailChange(value: String) {
            form.update { it.copy(email = value, error = null) }
        }

        fun onPasswordChange(value: String) {
            form.update { it.copy(password = value, error = null) }
        }

        fun onConfirmPasswordChange(value: String) {
            form.update { it.copy(confirmPassword = value, error = null) }
        }

        fun onTogglePasswordVisible() {
            form.update { it.copy(passwordVisible = !it.passwordVisible) }
        }

        fun signUp() {
            val current = form.value
            if (!current.submitEnabled) return
            viewModelScope.launch {
                form.update { it.copy(inProgress = true) }
                when (val result = authRepository.signUpWithEmail(current.email.trim(), current.password)) {
                    is AppResult.Success -> {
                        eventChannel.send(SignUpEvent.SignedUp)
                    }

                    is AppResult.Failure -> {
                        val error = result.error as? AuthError ?: AuthError.Unknown(null, result.error)
                        form.update { it.copy(inProgress = false, error = error) }
                    }
                }
            }
        }
    }
