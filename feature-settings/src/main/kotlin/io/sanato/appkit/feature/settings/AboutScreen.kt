package io.sanato.appkit.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import io.sanato.appkit.core.common.AppBuildInfo
import io.sanato.appkit.core.ui.components.AppScaffold
import io.sanato.appkit.core.ui.theme.AppTheme

/**
 * 无状态版本:`buildInfo` 由调用方提供(不用 Hilt 就自己
 * `AppBuildInfo.fromPackageManager(context)`),`changelog` 传空列表就不显示
 * 更新日志段落。`onNavigateToLicenses` 为 `null` 时不显示开源许可入口——
 * 消费方没有引入 `:feature-licenses` 时就是这样。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    buildInfo: AppBuildInfo,
    changelog: List<ChangelogEntry>,
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: (() -> Unit)? = null,
) {
    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appkit_settings_about)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text(buildInfo.appLabel) },
                    supportingContent = {
                        Text(
                            "${buildInfo.versionName} (${buildInfo.versionCode}) · " +
                                "${buildInfo.gitSha} · ${buildInfo.formattedBuildTime()}",
                        )
                    },
                )
            }
            if (onNavigateToLicenses != null) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.appkit_about_licenses)) },
                        modifier = Modifier.clickable(onClick = onNavigateToLicenses),
                    )
                }
            }
            if (changelog.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.appkit_about_changelog_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(AppTheme.spacing.md),
                    )
                }
                items(changelog) { entry ->
                    ListItem(
                        headlineContent = { Text("${entry.versionName} · ${entry.date}") },
                        supportingContent = { Text(entry.highlights.joinToString("\n") { "• $it" }) },
                    )
                }
            }
        }
    }
}
