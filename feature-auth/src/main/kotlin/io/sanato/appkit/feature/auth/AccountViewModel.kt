package io.sanato.appkit.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.appkit.core.auth.AuthRepository
import io.sanato.appkit.core.auth.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val email: String? = null,
    val displayName: String? = null,
    val phoneNumber: String? = null,
    val signingOut: Boolean = false,
)

@HiltViewModel
class AccountViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val signingOut = MutableStateFlow(false)

        val uiState: StateFlow<AccountUiState> =
            combine(authRepository.authState, signingOut) { state, isSigningOut ->
                val user = (state as? AuthState.SignedIn)?.user
                AccountUiState(
                    email = user?.email,
                    displayName = user?.displayName,
                    phoneNumber = user?.phoneNumber,
                    signingOut = isSigningOut,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AccountUiState())

        /**
         * Only flips [AuthState] — this ViewModel does not navigate anywhere.
         * [AuthSessionHost] is the single place that reacts to a
         * `SignedIn → SignedOut` transition and clears the back stack; see its
         * KDoc for why a second navigator here would race with it.
         */
        fun signOut() {
            viewModelScope.launch {
                signingOut.value = true
                authRepository.signOut()
                // No need to reset signingOut back to false on success — AuthSessionHost
                // navigates this screen out of existence the moment authState flips.
                // On failure it does matter, so it's reset either way.
                signingOut.value = false
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
