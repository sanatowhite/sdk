package io.sanato.appkit.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AccountRoute(
    onNavigateBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AccountScreen(
        email = uiState.email,
        displayName = uiState.displayName,
        phoneNumber = uiState.phoneNumber,
        signingOut = uiState.signingOut,
        onSignOut = viewModel::signOut,
        onNavigateBack = onNavigateBack,
    )
}
