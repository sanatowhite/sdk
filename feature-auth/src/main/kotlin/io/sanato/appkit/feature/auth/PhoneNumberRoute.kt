package io.sanato.appkit.feature.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PhoneNumberRoute(
    onCodeSent: (verificationId: String, phoneNumberMasked: String) -> Unit,
    onSignedIn: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PhoneNumberViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PhoneNumberEvent.CodeSent -> onCodeSent(event.verificationId, event.phoneNumberMasked)
                PhoneNumberEvent.SignedIn -> onSignedIn()
            }
        }
    }

    PhoneNumberScreen(
        phoneNumber = uiState.phoneNumber,
        submitEnabled = uiState.submitEnabled,
        inProgress = uiState.inProgress,
        errorMessage = uiState.error?.let { authErrorMessage(it) },
        onPhoneNumberChange = viewModel::onPhoneNumberChange,
        onSubmit = { activity?.let { viewModel.sendCode(it) } },
        onNavigateBack = onNavigateBack,
    )
}
