package io.sanato.apptemplate.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.sanato.appkit.core.common.UiState
import io.sanato.appkit.core.ui.components.AppScaffold
import io.sanato.appkit.core.ui.components.StateContent
import io.sanato.appkit.core.ui.theme.AppTheme
import io.sanato.appkit.feature.settings.StandardPagesContent
import io.sanato.appkit.feature.settings.WhatsNewRoute
import io.sanato.apptemplate.R
import io.sanato.appkit.feature.settings.R as SettingsR

/**
 * Phase 4 骨架页面证明了 core-ui 的壳层/状态组件 + 类型安全导航接线正确;
 * Phase 8 在这里加上设置入口 + What's New 弹窗接入点(来自 :feature-settings)。
 * 真正读业务数据的版本要等具体 feature 落地时再替换这里的占位 [UiState]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToSettings: () -> Unit) {
    val state = remember { UiState.success("Hello, App Template!") }

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
        StateContent(state = state, onRetry = {}) { message ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(AppTheme.spacing.md),
            ) {
                Text(text = message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    WhatsNewRoute(content = StandardPagesContent(changelogRawRes = R.raw.changelog))
}
