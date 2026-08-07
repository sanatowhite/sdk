package io.sanato.appkit.feature.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

/**
 * Hilt 消费方的入口——包一层 `hiltViewModel()`,截图/日志勾选状态是纯 UI 状态,
 * 留在这里(不进 ViewModel),把 [FeedbackScreen] 需要的一切从状态+回调里传下去。
 * 不用 Hilt 就直接用 [FeedbackScreen]。
 *
 * "附带截图"需要宿主用 [FeedbackScreenshotHost] 包住内容根节点,否则
 * [FeedbackScreenshot.capture] 永远返回 null——不会崩,只是截不到图。
 */
@Composable
fun FeedbackRoute(
    onNavigateBack: () -> Unit,
    viewModel: FeedbackViewModel = hiltViewModel(),
) {
    var description by remember { mutableStateOf("") }
    var includeScreenshot by remember { mutableStateOf(true) }
    var includeLogs by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    FeedbackScreen(
        description = description,
        includeScreenshot = includeScreenshot,
        includeLogs = includeLogs,
        onDescriptionChange = { description = it },
        onIncludeScreenshotChange = { includeScreenshot = it },
        onIncludeLogsChange = { includeLogs = it },
        onSend = {
            scope.launch {
                val screenshot = if (includeScreenshot) FeedbackScreenshot.capture()?.asAndroidBitmap() else null
                viewModel.sendFeedback(description, screenshot, includeLogs)
            }
        },
        onNavigateBack = onNavigateBack,
    )
}
