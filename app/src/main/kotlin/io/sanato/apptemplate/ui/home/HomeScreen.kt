package io.sanato.apptemplate.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.sanato.apptemplate.R
import io.sanato.apptemplate.core.common.UiState
import io.sanato.apptemplate.core.ui.components.AppScaffold
import io.sanato.apptemplate.core.ui.components.StateContent
import io.sanato.apptemplate.core.ui.theme.AppTheme

/**
 * Phase 4 的骨架页面:只用来证明 core-ui 的壳层/状态组件 + 类型安全导航接线正确。
 * 真正读数据的版本要等 Phase 5(core-data/core-net)接进来之后再替换这里的
 * 占位 [UiState]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val state = remember { UiState.success("Hello, App Template!") }

    AppScaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
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
}
