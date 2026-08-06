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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.sanato.apptemplate.R
import io.sanato.apptemplate.core.common.UiState
import io.sanato.apptemplate.core.ui.components.AppScaffold
import io.sanato.apptemplate.core.ui.components.StateContent
import io.sanato.apptemplate.core.ui.theme.AppTheme
import io.sanato.apptemplate.whatsnew.WhatsNewSheet
import io.sanato.apptemplate.whatsnew.WhatsNewViewModel

/**
 * Phase 4 骨架页面证明了 core-ui 的壳层/状态组件 + 类型安全导航接线正确;
 * Phase 7 在这里加上设置入口 + What's New 弹窗接入点。真正读业务数据的版本
 * 要等具体 feature 落地时再替换这里的占位 [UiState]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    whatsNewViewModel: WhatsNewViewModel = hiltViewModel(),
) {
    val state = remember { UiState.success("Hello, App Template!") }
    val shouldShowWhatsNew by whatsNewViewModel.shouldShow.collectAsStateWithLifecycle()

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
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

    // 全新安装也要标记已读(见 WhatsNewViewModel 的说明),不只是"展示时才标记"——
    // 但必须等 shouldShowWhatsNew 从 null 变成确定值之后才能下判断,见该字段的注释。
    when (shouldShowWhatsNew) {
        true -> WhatsNewSheet(onDismiss = { whatsNewViewModel.markSeen() })
        false -> LaunchedEffect(Unit) { whatsNewViewModel.markSeen() }
        null -> Unit
    }
}
