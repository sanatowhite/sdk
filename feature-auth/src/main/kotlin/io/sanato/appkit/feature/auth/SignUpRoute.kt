package io.sanato.appkit.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SignUpRoute(
    onSignedUp: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SignUpEvent.SignedUp -> onSignedUp()
            }
        }
    }

    SignUpScreen(
        email = uiState.email,
        password = uiState.password,
        confirmPassword = uiState.confirmPassword,
        passwordVisible = uiState.passwordVisible,
        submitEnabled = uiState.submitEnabled,
        inProgress = uiState.inProgress,
        errorMessage = uiState.error?.let { authErrorMessage(it) },
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onTogglePasswordVisible = viewModel::onTogglePasswordVisible,
        onSubmit = viewModel::signUp,
        onNavigateBack = onNavigateBack,
    )
}
