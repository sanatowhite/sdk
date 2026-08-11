package io.sanato.appkit.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.appkit.core.auth.AuthRepository
import io.sanato.appkit.core.auth.AuthState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val SPLASH_TIMEOUT_MILLIS = 1_500L

/**
 * Decides whether the consumer's `NavHost` should start at [AuthGraphRoute].
 * Shape deliberately mirrors `:feature-settings`'s
 * `AppEntryViewModel.consentRequired` — a single `StateFlow<Boolean?>`, `null`
 * = still unknown, the consumer composes its own `startDestination` from it.
 *
 * The timeout fallback resolves to `true` (show sign-in) rather than `false`
 * — asymmetric on purpose. Guessing `true` when actually signed in costs one
 * extra frame: [SignInRoute] observes `authState` and calls `onSignedIn()`
 * itself the instant it flips to `SignedIn`. Guessing `false` when actually
 * signed out would strand the user on a home screen that needs a session it
 * doesn't have, with nothing to self-correct it.
 */
@HiltViewModel
class AuthEntryViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
    ) : ViewModel() {
        val signInRequired: StateFlow<Boolean?> =
            flow {
                val resolved =
                    withTimeoutOrNull(SPLASH_TIMEOUT_MILLIS) {
                        authRepository.authState.first { it !is AuthState.Unknown }
                    }
                emit(resolved !is AuthState.SignedIn)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }
