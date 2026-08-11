package io.sanato.apptemplate.ui.websocket

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Hilt 入口——包一层 `hiltViewModel()`,把 [WebSocketDemoScreen] 需要的状态从
 * [WebSocketDemoViewModel] 里取出来。
 */
@Composable
fun WebSocketDemoRoute(
    onNavigateBack: () -> Unit,
    viewModel: WebSocketDemoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WebSocketDemoScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::send,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
    )
}
