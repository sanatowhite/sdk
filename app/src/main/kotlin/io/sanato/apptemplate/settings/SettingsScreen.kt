package io.sanato.apptemplate.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.sanato.apptemplate.R
import io.sanato.apptemplate.core.data.ThemeMode
import io.sanato.apptemplate.core.ui.components.AppScaffold
import io.sanato.apptemplate.core.ui.theme.AppTheme

private val SUPPORTED_LANGUAGE_TAGS = listOf(null, "en", "zh-Hans")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToTermsOfService: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            item {
                ThemeModeRow(current = uiState.themeMode, onSelect = viewModel::setThemeMode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        checked = uiState.dynamicColorEnabled,
                        onCheckedChange = viewModel::setDynamicColorEnabled,
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_language)) },
                    supportingContent = { Text(languageLabel(LocaleManager.currentAppLocaleTag())) },
                    modifier = Modifier.clickable { showLanguageDialog = true },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_privacy)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_telemetry),
                    checked = uiState.telemetryEnabled,
                    onCheckedChange = viewModel::setTelemetryEnabled,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_notifications),
                    checked = uiState.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setNotificationsEnabled(enabled)
                        // API 26 起通知需要走系统设置授权,这里直接跳转过去而不是自己
                        // 弹权限请求——通知权限本身(POST_NOTIFICATIONS)是 API 33+ 才有
                        // 的运行时权限,26-32 只需要用户在系统设置里手动允许。
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_privacy_policy)) },
                    modifier = Modifier.clickable(onClick = onNavigateToPrivacyPolicy),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_terms_of_service)) },
                    modifier = Modifier.clickable(onClick = onNavigateToTermsOfService),
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_about)) },
                    modifier = Modifier.clickable(onClick = onNavigateToAbout),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_clear_cache)) },
                    modifier = Modifier.clickable { context.cacheDir.deleteRecursively() },
                )
            }
        }
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            onDismiss = { showLanguageDialog = false },
            onSelect = { tag ->
                LocaleManager.setAppLocale(tag)
                showLanguageDialog = false
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.sm),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun ThemeModeRow(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_theme)) },
        supportingContent = {
            Row {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onSelect(mode) },
                    ) {
                        RadioButton(selected = mode == current, onClick = { onSelect(mode) })
                        Text(themeModeLabel(mode))
                    }
                }
            }
        },
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    }

@Composable
private fun languageLabel(tag: String?): String =
    when (tag) {
        null -> stringResource(R.string.settings_language_system)
        "en" -> "English"
        else -> "简体中文"
    }

@Composable
private fun LanguagePickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                SUPPORTED_LANGUAGE_TAGS.forEach { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onSelect(tag) },
                    ) {
                        RadioButton(
                            selected = tag == LocaleManager.currentAppLocaleTag(),
                            onClick = { onSelect(tag) },
                        )
                        Text(languageLabel(tag))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
