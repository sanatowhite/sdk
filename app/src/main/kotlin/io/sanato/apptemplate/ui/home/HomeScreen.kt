package io.sanato.apptemplate.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.sanato.appkit.core.ui.components.AppScaffold
import io.sanato.appkit.core.ui.theme.AppTheme
import io.sanato.appkit.feature.settings.StandardPagesContent
import io.sanato.appkit.feature.settings.WhatsNewRoute
import io.sanato.apptemplate.R
import io.sanato.appkit.feature.settings.R as SettingsR

/**
 * Home 是这个模板的功能入口聚合页,不是某个具体业务的首页——每加一个可以独立体验
 * 的能力(反馈/许可/更新检查/账号/WebSocket demo),就在这里加一行,而不是散落在
 * 各处让人自己去找。跨 feature 的入口全部走非空回调直接接线(不像 `settingsGraph`
 * 那样用可空回调解耦)——因为 `HomeScreen` 本来就活在 `:app` 里,`:app` 已经依赖
 * 了下面用到的每一个模块,这里没有"零依赖边"要保护。
 *
 * [accountSubtitle] 是当前登录邮箱/手机号,由 `MainActivity`/`AppNavHost` 从
 * `AuthState` 派生后传进来——见 `AppNavHost.kt` 的同名参数说明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    accountSubtitle: String?,
    onNavigateToSettings: () -> Unit,
    onNavigateToFeedback: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToWebSocketDemo: () -> Unit,
) {
    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(SettingsR.string.appkit_settings_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                SectionHeader(stringResource(R.string.home_section_features))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.home_account)) },
                    supportingContent = accountSubtitle?.let { { Text(it) } },
                    modifier = Modifier.clickable(onClick = onNavigateToAccount),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.home_websocket_demo)) },
                    supportingContent = { Text(stringResource(R.string.home_websocket_demo_subtitle)) },
                    modifier = Modifier.clickable(onClick = onNavigateToWebSocketDemo),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.home_check_updates)) },
                    modifier = Modifier.clickable(onClick = onCheckForUpdates),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.home_feedback)) },
                    supportingContent = { Text(stringResource(R.string.home_feedback_subtitle)) },
                    modifier = Modifier.clickable(onClick = onNavigateToFeedback),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.home_licenses)) },
                    modifier = Modifier.clickable(onClick = onNavigateToLicenses),
                )
            }
        }
    }

    WhatsNewRoute(content = StandardPagesContent(changelogRawRes = R.raw.changelog))
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
