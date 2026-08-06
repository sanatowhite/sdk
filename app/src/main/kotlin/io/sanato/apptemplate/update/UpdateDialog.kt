package io.sanato.apptemplate.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.sanato.apptemplate.R
import io.sanato.updatechecker.UpdateInfo
import java.io.File

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateUiState.Available -> {
            AlertDialog(
                // force 更新不能被手势/返回键关掉——只有下载完成一条路径能离开这个对话框。
                onDismissRequest = { if (!state.info.force) onDismiss() },
                title = { Text(stringResource(R.string.update_available_title, state.info.versionName)) },
                text = { Text(state.info.releaseNotes) },
                confirmButton = {
                    Button(onClick = { onDownload(state.info) }) { Text(stringResource(R.string.update_download)) }
                },
                dismissButton =
                    if (!state.info.force) {
                        { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
                    } else {
                        null
                    },
            )
        }

        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.update_downloading)) },
                text = {
                    if (state.totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { state.bytesDownloaded.toFloat() / state.totalBytes.toFloat() },
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                },
                confirmButton = {},
            )
        }

        is UpdateUiState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.update_ready_title)) },
                text = { Text(stringResource(R.string.update_ready_body)) },
                confirmButton = {
                    Button(onClick = { onInstall(state.file) }) { Text(stringResource(R.string.update_install)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
            )
        }

        is UpdateUiState.DownloadFailed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.update_failed_title)) },
                text = { Text(state.reason) },
                confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
            )
        }

        // Idle/Checking/UpToDate/Error 不弹对话框——调用方(SettingsScreen)用
        // Toast 展示这几个瞬时结果,不需要额外的模态打断。
        UpdateUiState.Idle, UpdateUiState.Checking, UpdateUiState.UpToDate, is UpdateUiState.Error -> {
            Unit
        }
    }
}
