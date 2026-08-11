package io.sanato.appkit.feature.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.appkit.core.auth.AuthError
import io.sanato.appkit.core.auth.AuthProvider
import io.sanato.appkit.core.auth.AuthRepository
import io.sanato.appkit.core.auth.AuthUser
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

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val availableProviders: List<AuthProvider> = emptyList(),
    /** Non-null ⇒ that provider's button is spinning, every other control is disabled. */
    val inProgress: AuthProvider? = null,
    val error: AuthError? = null,
) {
    val submitEnabled: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && inProgress == null
}

sealed interface SignInEvent {
    data object SignedIn : SignInEvent
}

@HiltViewModel
class SignInViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val form = MutableStateFlow(SignInUiState())
        val uiState: StateFlow<SignInUiState> = form.asStateFlow()

        /**
         * One-shot navigation events go through a `Channel`, not the `StateFlow`
         * above — a `StateFlow` replays its last value on every new collector
         * (e.g. after a configuration change), which would fire "navigate to
         * home" a second time.
         */
        private val eventChannel = Channel<SignInEvent>(Channel.BUFFERED)
        val events: Flow<SignInEvent> = eventChannel.receiveAsFlow()

        init {
            viewModelScope.launch {
                val providers = authRepository.availableProviders().toList()
                form.update { it.copy(availableProviders = providers) }
            }
        }

        fun onEmailChange(value: String) {
            form.update { it.copy(email = value, error = null) }
        }

        fun onPasswordChange(value: String) {
            form.update { it.copy(password = value, error = null) }
        }

        fun onTogglePasswordVisible() {
            form.update { it.copy(passwordVisible = !it.passwordVisible) }
        }

        fun signInWithEmailPassword() {
            val current = form.value
            if (!current.submitEnabled) return
            viewModelScope.launch {
                form.update { it.copy(inProgress = AuthProvider.Password, error = null) }
                handle(authRepository.signInWithEmail(current.email.trim(), current.password))
            }
        }

        /**
         * [activity] is a method parameter, never a field — Google's Credential
         * Manager and Apple's `startActivityForSignInWithProvider` both require
         * one by signature. Keeping it as a parameter means it only lives on
         * this coroutine's stack frame for the duration of the call, and the
         * OAuth UI is what's keeping the host `Activity` alive during that
         * window anyway.
         */
        fun signInWithProvider(
            provider: AuthProvider,
            activity: Activity,
        ) {
            viewModelScope.launch {
                form.update { it.copy(inProgress = provider, error = null) }
                val result =
                    when (provider) {
                        AuthProvider.Google -> authRepository.signInWithGoogle(activity)
                        AuthProvider.Apple -> authRepository.signInWithApple(activity)
                        else -> return@launch
                    }
                handle(result)
            }
        }

        fun dismissError() {
            form.update { it.copy(error = null) }
        }

        private suspend fun handle(result: AppResult<AuthUser>) {
            form.update { it.copy(inProgress = null) }
            when (result) {
                is AppResult.Success -> {
                    eventChannel.send(SignInEvent.SignedIn)
                }

                is AppResult.Failure -> {
                    val error = result.error as? AuthError ?: AuthError.Unknown(null, result.error)
                    when (error) {
                        // User dismissed the provider UI — nothing went wrong, nothing to show.
                        is AuthError.Cancelled -> Unit

                        // v1 doesn't offer an account-linking flow (see :core-auth's README) — the
                        // error message itself ("please use the provider you originally signed up
                        // with") is the whole resolution UI for now.
                        else -> form.update { it.copy(error = error) }
                    }
                }
            }
        }
    }
