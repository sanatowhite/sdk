package io.sanato.appkit.feature.settings

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ConsentRoute(
    onAccepted: () -> Unit,
    onViewPrivacyPolicy: () -> Unit,
    onViewTermsOfService: () -> Unit,
    viewModel: ConsentViewModel = hiltViewModel(),
) {
    ConsentScreen(
        onAccept = { viewModel.accept(onAccepted) },
        onViewPrivacyPolicy = onViewPrivacyPolicy,
        onViewTermsOfService = onViewTermsOfService,
    )
}
