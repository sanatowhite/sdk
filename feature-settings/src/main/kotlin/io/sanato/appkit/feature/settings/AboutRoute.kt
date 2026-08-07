package io.sanato.appkit.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AboutRoute(
    content: StandardPagesContent = StandardPagesContent(),
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: (() -> Unit)? = null,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val changelog =
        remember(content.changelogRawRes) {
            content.changelogRawRes?.let { ChangelogReader.read(context, it) } ?: emptyList()
        }

    AboutScreen(
        buildInfo = viewModel.buildInfo,
        changelog = changelog,
        onNavigateBack = onNavigateBack,
        onNavigateToLicenses = onNavigateToLicenses,
    )
}
