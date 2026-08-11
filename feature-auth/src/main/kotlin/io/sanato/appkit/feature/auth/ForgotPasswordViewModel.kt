package io.sanato.appkit.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.appkit.core.auth.AuthError
import io.sanato.appkit.core.auth.AuthRepository
import io.sanato.appkit.core.common.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForgotPasswordUiState(
    val email: String = "",
    val inProgress: Boolean = false,
    /** Once true, the screen shows "check your email" instead of the form — there's nothing more to do here. */
    val emailSent: Boolean = false,
    val error: AuthError? = null,
) {
    val submitEnabled: Boolean get() = email.isNotBlank() && !inProgress
}

@HiltViewModel
class ForgotPasswordViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val form = MutableStateFlow(ForgotPasswordUiState())
        val uiState: StateFlow<ForgotPasswordUiState> = form.asStateFlow()

        fun onEmailChange(value: String) {
            form.update { it.copy(email = value, error = null) }
        }

        fun sendResetEmail() {
            val current = form.value
            if (!current.submitEnabled) return
            viewModelScope.launch {
                form.update { it.copy(inProgress = true) }
                when (val result = authRepository.sendPasswordResetEmail(current.email.trim())) {
                    is AppResult.Success -> {
                        form.update { it.copy(inProgress = false, emailSent = true) }
                    }

                    is AppResult.Failure -> {
                        val error = result.error as? AuthError ?: AuthError.Unknown(null, result.error)
                        form.update { it.copy(inProgress = false, error = error) }
                    }
                }
            }
        }
    }
