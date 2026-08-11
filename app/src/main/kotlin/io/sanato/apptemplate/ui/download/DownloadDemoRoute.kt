package io.sanato.apptemplate.ui.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Hilt entry point — thin wrapper matching `ui/websocket/WebSocketDemoRoute`'s shape. */
@Composable
fun DownloadDemoRoute(
    onNavigateBack: () -> Unit,
    viewModel: DownloadDemoViewModel = hiltViewModel(),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    DownloadDemoScreen(
        tasks = tasks,
        onNavigateBack = onNavigateBack,
        onAddSample = viewModel::enqueueSample,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onCancel = viewModel::cancel,
        onCancelAll = viewModel::cancelAll,
    )
}
