package io.sanato.appkit.feature.update

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 持有更新检查的跨屏幕状态 + 展示 [UpdateDialog] + UpToDate/Error 的 Toast 提示,
 * 一行包住你自己的 `NavHost`:
 *
 * ```kotlin
 * UpdateCheckHost { onCheckForUpdates ->
 *     NavHost(navController, startDestination = Home) {
 *         composable<Home> { ... }
 *         settingsGraph(navController, onCheckForUpdates = onCheckForUpdates)   // 引了 :feature-settings 才传
 *     }
 * }
 * ```
 *
 * [content] 拿到的 `onCheckForUpdates` 回调不知道也不需要知道谁触发它——
 * 不用 `:feature-settings` 也可以自己接一个按钮调用它。不想要默认 Toast/弹窗
 * 行为?直接用 [UpdateViewModel] + [UpdateDialog] 自己接线。
 */
@Composable
fun UpdateCheckHost(
    viewModel: UpdateViewModel = hiltViewModel(),
    content: @Composable (onCheckForUpdates: () -> Unit) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 用 stringResource() 而不是 Toast 里直接 context.getString()——后者读的是
    // LocalContext.current 那一刻的资源,Configuration(比如语言)变化不会让它
    // 重新求值。
    val upToDateMessage = stringResource(R.string.appkit_update_up_to_date)

    LaunchedEffect(state) {
        when (val current = state) {
            UpdateUiState.UpToDate -> Toast.makeText(context, upToDateMessage, Toast.LENGTH_SHORT).show()
            is UpdateUiState.Error -> Toast.makeText(context, current.message, Toast.LENGTH_LONG).show()
            else -> Unit
        }
    }

    content(viewModel::checkForUpdate)

    UpdateDialog(
        state = state,
        onDownload = viewModel::startDownload,
        onInstall = viewModel::install,
        onDismiss = viewModel::dismiss,
    )
}
