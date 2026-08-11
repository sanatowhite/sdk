package io.sanato.apptemplate.ui.websocket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import io.sanato.appkit.core.net.ws.WebSocketState
import io.sanato.appkit.core.ui.components.AppScaffold
import io.sanato.appkit.core.ui.theme.AppTheme
import io.sanato.apptemplate.R

/**
 * 无状态版本——`WebSocketDemoRoute` 负责接 `hiltViewModel()`。所有数据/回调都是
 * 参数,方便以后要给这个 demo 补 Roborazzi 基线时直接用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSocketDemoScreen(
    uiState: WebSocketDemoUiState,
    onNavigateBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.log.size) {
        if (uiState.log.isNotEmpty()) {
            listState.animateScrollToItem(uiState.log.lastIndex)
        }
    }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ws_demo_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(AppTheme.spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uiState.connectionState.describe(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (uiState.isConnected) {
                    OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.ws_demo_disconnect)) }
                } else {
                    OutlinedButton(onClick = onConnect) { Text(stringResource(R.string.ws_demo_connect)) }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = AppTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
            ) {
                if (uiState.log.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.ws_demo_empty_log),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(uiState.log) { entry ->
                    WebSocketLogRow(entry)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.ws_demo_input_hint)) },
                    singleLine = true,
                )
                Button(onClick = onSend, enabled = uiState.canSend) {
                    Text(stringResource(R.string.ws_demo_send))
                }
            }
        }
    }
}

@Composable
private fun WebSocketLogRow(entry: WebSocketLogEntry) {
    val (prefix, style) =
        when (entry.direction) {
            WebSocketLogDirection.SENT -> {
                "→ " to MaterialTheme.typography.bodyMedium
            }

            WebSocketLogDirection.RECEIVED -> {
                "← " to MaterialTheme.typography.bodyMedium
            }

            WebSocketLogDirection.SYSTEM -> {
                "· " to MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic)
            }
        }
    Text(text = prefix + entry.text, style = style)
}

@Composable
private fun WebSocketState.describe(): String =
    when (this) {
        WebSocketState.Idle -> stringResource(R.string.ws_demo_state_idle)
        WebSocketState.Connecting -> stringResource(R.string.ws_demo_state_connecting)
        WebSocketState.Connected -> stringResource(R.string.ws_demo_state_connected)
        is WebSocketState.Reconnecting -> stringResource(R.string.ws_demo_state_reconnecting, attempt)
        is WebSocketState.Closed -> stringResource(R.string.ws_demo_state_closed, code)
        is WebSocketState.Failed -> stringResource(R.string.ws_demo_state_failed, error.message ?: "")
    }
