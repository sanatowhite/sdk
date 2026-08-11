package io.sanato.appkit.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * ⚠️ Known limitation: `route.verificationId` is a Firebase-internal
 * verification session handle. It survives process death as a nav-arg string,
 * but the Firebase verification session behind it does not — Firebase tracks
 * that purely in memory. A `confirmPhoneVerificationCode` call after process
 * death will fail (typically [io.sanato.appkit.core.auth.AuthError.VerificationCodeExpired]),
 * which the [onNavigateBack] path (back to phone-number entry) already
 * handles gracefully: there's no special "session lost" event, just an error
 * message on this screen prompting the user to go back and request a new code.
 */
@Composable
fun PhoneCodeRoute(
    onVerified: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PhoneCodeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PhoneCodeEvent.Verified -> onVerified()
            }
        }
    }

    PhoneCodeScreen(
        phoneNumberMasked = uiState.phoneNumberMasked,
        code = uiState.code,
        codeLength = uiState.codeLength,
        verifying = uiState.verifying,
        resendSecondsRemaining = uiState.resendSecondsRemaining,
        errorMessage = uiState.error?.let { authErrorMessage(it) },
        onCodeChange = viewModel::onCodeChange,
        onResend = onNavigateBack,
        onNavigateBack = onNavigateBack,
    )
}
