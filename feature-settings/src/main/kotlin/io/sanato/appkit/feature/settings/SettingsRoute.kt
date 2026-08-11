package io.sanato.appkit.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Hilt 消费方的入口——包一层 `hiltViewModel()`,把 [SettingsScreen] 需要的状态
 * 从 [SettingsViewModel] 里取出来。不用 Hilt 就直接用 [SettingsScreen]。
 */
@Composable
fun SettingsRoute(
    config: SettingsPageConfig = SettingsPageConfig(),
    content: StandardPagesContent = StandardPagesContent(),
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToTermsOfService: () -> Unit,
    onNavigateToFeedback: (() -> Unit)? = null,
    onCheckForUpdates: (() -> Unit)? = null,
    onNavigateToAccount: (() -> Unit)? = null,
    accountSubtitle: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        themeMode = uiState.themeMode,
        dynamicColorEnabled = uiState.dynamicColorEnabled,
        telemetryEnabled = uiState.telemetryEnabled,
        notificationsEnabled = uiState.notificationsEnabled,
        config = config,
        content = content,
        onNavigateBack = onNavigateBack,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColorEnabled,
        onTelemetryChange = viewModel::setTelemetryEnabled,
        onNotificationsChange = viewModel::setNotificationsEnabled,
        onNavigateToAbout = onNavigateToAbout,
        onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy,
        onNavigateToTermsOfService = onNavigateToTermsOfService,
        onNavigateToFeedback = onNavigateToFeedback,
        onCheckForUpdates = onCheckForUpdates,
        onNavigateToAccount = onNavigateToAccount,
        accountSubtitle = accountSubtitle,
    )
}
