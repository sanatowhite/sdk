package io.sanato.apptemplate.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.sanato.apptemplate.core.common.UiState
import io.sanato.apptemplate.core.ui.R
import io.sanato.apptemplate.core.ui.theme.AppTheme

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(AppTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm, Alignment.CenterVertically),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null)
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(AppTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm, Alignment.CenterVertically),
    ) {
        Icon(Icons.Outlined.Warning, contentDescription = null)
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) { Text(stringResource(R.string.core_ui_retry)) }
    }
}

/**
 * [UiState] 的通用渲染器:四态互斥优先级 loading > error > empty > content,
 * 消费方只需要提供最后一种"有数据"情况下怎么画。
 */
@Composable
fun <T> StateContent(
    state: UiState<T>,
    onRetry: () -> Unit,
    emptyMessage: String = stringResource(R.string.core_ui_empty_default),
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    // 跨模块的 val 属性(UiState 定义在 core-common)不能直接 smart cast,
    // 先捕获成局部 val。
    val data = state.data
    val error = state.error
    val defaultErrorMessage = stringResource(R.string.core_ui_error_default)
    when {
        state.isLoading && data == null -> {
            LoadingState(modifier)
        }

        error != null && data == null -> {
            ErrorState(
                message = error.message ?: defaultErrorMessage,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        data == null -> {
            EmptyState(emptyMessage, modifier)
        }

        else -> {
            content(data)
        }
    }
}
