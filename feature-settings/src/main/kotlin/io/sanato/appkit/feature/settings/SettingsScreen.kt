package io.sanato.appkit.feature.settings

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
import io.sanato.appkit.core.data.ThemeMode
import io.sanato.appkit.core.ui.components.AppScaffold
import io.sanato.appkit.core.ui.theme.AppTheme
import java.util.Locale

/**
 * 无状态版本——所有数据/回调都是参数,不碰 `hiltViewModel()`,可以直接用于
 * Roborazzi 截图测试,也是不用 Hilt 的消费方唯一需要用到的入口(配合自己的
 * ViewModel/状态管理)。用 Hilt 的消费方走 [SettingsRoute] 更省事。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    dynamicColorEnabled: Boolean,
    telemetryEnabled: Boolean,
    notificationsEnabled: Boolean,
    config: SettingsPageConfig,
    content: StandardPagesContent,
    onNavigateBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onTelemetryChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToTermsOfService: () -> Unit,
    modifier: Modifier = Modifier,
    // null ⇒ 对应入口不显示——消费方没有引入那个能力(反馈页/更新检查)时就是这样。
    onNavigateToFeedback: (() -> Unit)? = null,
    onCheckForUpdates: (() -> Unit)? = null,
    // 尾部追加,同上面两个回调的模式——消费方没有引入 :feature-auth 就不传,
    // "账号"区块整体不显示。accountSubtitle 是当前登录邮箱/手机号等展示串,由
    // 消费方从自己的 AuthState 里派生,这个模块不知道 AuthState 长什么样。
    onNavigateToAccount: (() -> Unit)? = null,
    accountSubtitle: String? = null,
) {
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }

    AppScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appkit_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (onNavigateToAccount != null) {
                item { SectionHeader(stringResource(R.string.appkit_settings_section_account)) }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.appkit_settings_account)) },
                        supportingContent = accountSubtitle?.let { { Text(it) } },
                        modifier = Modifier.clickable(onClick = onNavigateToAccount),
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.appkit_settings_section_appearance)) }
            item {
                ThemeModeRow(current = themeMode, onSelect = onThemeModeChange)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchRow(
                        title = stringResource(R.string.appkit_settings_dynamic_color),
                        checked = dynamicColorEnabled,
                        onCheckedChange = onDynamicColorChange,
                    )
                }
            }
            if (config.supportedLanguageTags != null) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.appkit_settings_language)) },
                        supportingContent = { Text(languageLabel(LocaleManager.currentAppLocaleTag())) },
                        modifier = Modifier.clickable { showLanguageDialog = true },
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.appkit_settings_section_privacy)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.appkit_settings_telemetry),
                    checked = telemetryEnabled,
                    onCheckedChange = onTelemetryChange,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.appkit_settings_notifications),
                    checked = notificationsEnabled,
                    onCheckedChange = { enabled ->
                        onNotificationsChange(enabled)
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
            if (content.privacyPolicyRawRes != null) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.appkit_settings_privacy_policy)) },
                        modifier = Modifier.clickable(onClick = onNavigateToPrivacyPolicy),
                    )
                }
            }
            if (content.termsOfServiceRawRes != null) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.appkit_settings_terms_of_service)) },
                        modifier = Modifier.clickable(onClick = onNavigateToTermsOfService),
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.appkit_settings_section_about)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.appkit_settings_about)) },
                    modifier = Modifier.clickable(onClick = onNavigateToAbout),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.appkit_settings_clear_cache)) },
                    modifier = Modifier.clickable { context.cacheDir.deleteRecursively() },
                )
            }
            if (onCheckForUpdates != null) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.appkit_settings_check_for_updates)) },
                        modifier = Modifier.clickable(onClick = onCheckForUpdates),
                    )
                }
            }
            if (onNavigateToFeedback != null) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.appkit_settings_send_feedback)) },
                        modifier = Modifier.clickable(onClick = onNavigateToFeedback),
                    )
                }
            }
        }
    }

    if (showLanguageDialog && config.supportedLanguageTags != null) {
        LanguagePickerDialog(
            tags = config.supportedLanguageTags,
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
        headlineContent = { Text(stringResource(R.string.appkit_settings_theme)) },
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
        ThemeMode.SYSTEM -> stringResource(R.string.appkit_settings_theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.appkit_settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.appkit_settings_theme_dark)
    }

/**
 * 用 `Locale.getDisplayName` 生成展示名(用该语言自己的书写方式,比如
 * `zh-Hans` 显示"简体中文"、`en` 显示"English")——不需要为每个支持的语言
 * 硬编码一条标签文案,消费方新增语言不需要我们发新版本。
 */
@Composable
private fun languageLabel(tag: String?): String =
    when (tag) {
        null -> stringResource(R.string.appkit_settings_language_system)
        else -> Locale.forLanguageTag(tag).let { it.getDisplayName(it) }
    }

@Composable
private fun LanguagePickerDialog(
    tags: List<String?>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appkit_settings_language)) },
        text = {
            Column {
                tags.forEach { tag ->
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.appkit_action_close)) }
        },
    )
}
