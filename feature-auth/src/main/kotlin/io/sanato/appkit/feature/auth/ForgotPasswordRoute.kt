package io.sanato.appkit.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ForgotPasswordRoute(
    onNavigateBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ForgotPasswordScreen(
        email = uiState.email,
        submitEnabled = uiState.submitEnabled,
        inProgress = uiState.inProgress,
        emailSent = uiState.emailSent,
        errorMessage = uiState.error?.let { authErrorMessage(it) },
        onEmailChange = viewModel::onEmailChange,
        onSubmit = viewModel::sendResetEmail,
        onNavigateBack = onNavigateBack,
    )
}
