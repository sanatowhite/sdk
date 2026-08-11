package io.sanato.apptemplate.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.sanato.appkit.core.ui.components.AppScaffold
import io.sanato.appkit.core.ui.theme.AppTheme
import io.sanato.appkit.download.DownloadError
import io.sanato.appkit.download.DownloadState
import io.sanato.appkit.download.DownloadTask
import io.sanato.apptemplate.R

/** Stateless — `DownloadDemoRoute` owns the `hiltViewModel()`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDemoScreen(
    tasks: List<DownloadTask>,
    onNavigateBack: () -> Unit,
    onAddSample: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onCancelAll: () -> Unit,
) {
    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.download_demo_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (tasks.isNotEmpty()) {
                        TextButton(onClick = onCancelAll) { Text(stringResource(R.string.download_demo_cancel_all)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(AppTheme.spacing.md)) {
            Button(onClick = onAddSample) {
                Text(stringResource(R.string.download_demo_add_sample))
            }

            if (tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.download_demo_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppTheme.spacing.md),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadTaskRow(task, onPause = onPause, onResume = onResume, onCancel = onCancel)
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskRow(
    task: DownloadTask,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = task.request.fileName, style = MaterialTheme.typography.bodyLarge)
        Text(text = task.state.describe(), style = MaterialTheme.typography.bodySmall)

        val progress = (task.state as? DownloadState.Running)?.progress
        if (progress != null && progress >= 0f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (task.state.isPausable()) {
                OutlinedButton(onClick = { onPause(task.id) }) { Text(stringResource(R.string.download_demo_pause)) }
            } else if (task.state.isResumable()) {
                OutlinedButton(onClick = { onResume(task.id) }) { Text(stringResource(R.string.download_demo_resume)) }
            }
            if (task.state !is DownloadState.Completed && task.state !is DownloadState.Canceled) {
                TextButton(onClick = { onCancel(task.id) }) { Text(stringResource(R.string.download_demo_cancel)) }
            }
        }
    }
}

@Composable
private fun DownloadState.describe(): String =
    when (this) {
        DownloadState.Queued -> {
            stringResource(R.string.download_demo_state_queued)
        }

        is DownloadState.Running -> {
            val percent =
                if (progress >=
                    0f
                ) {
                    "${(progress * 100).toInt()}%"
                } else {
                    stringResource(R.string.download_demo_unknown_size)
                }
            stringResource(R.string.download_demo_state_running, percent)
        }

        is DownloadState.Paused -> {
            stringResource(R.string.download_demo_state_paused)
        }

        is DownloadState.Completed -> {
            stringResource(R.string.download_demo_state_completed)
        }

        is DownloadState.Failed -> {
            stringResource(R.string.download_demo_state_failed, error.describe())
        }

        DownloadState.Canceled -> {
            stringResource(R.string.download_demo_state_canceled)
        }
    }

private fun DownloadError.describe(): String = message ?: this::class.java.simpleName
