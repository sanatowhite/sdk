package io.sanato.apptemplate.about

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.sanato.apptemplate.R
import io.sanato.apptemplate.core.ui.components.AppScaffold
import io.sanato.apptemplate.core.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val changelog = remember { ChangelogReader.read(context) }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_about)) },
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
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    supportingContent = {
                        Text(
                            "${BuildInfo.versionName} (${BuildInfo.versionCode}) · " +
                                "${BuildInfo.gitSha} · ${BuildInfo.formattedBuildTime()}",
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_licenses)) },
                    modifier = Modifier.clickable(onClick = onNavigateToLicenses),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.about_changelog_title),
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
