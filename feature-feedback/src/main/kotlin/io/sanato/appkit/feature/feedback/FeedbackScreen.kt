package io.sanato.appkit.feature.feedback

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.sanato.appkit.core.ui.components.AppScaffold
import io.sanato.appkit.core.ui.theme.AppTheme

/**
 * 无状态版本——不用 Hilt 想接自己的 ViewModel 也可以直接用这个。默认接线见
 * [FeedbackRoute]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    description: String,
    includeScreenshot: Boolean,
    includeLogs: Boolean,
    onDescriptionChange: (String) -> Unit,
    onIncludeScreenshotChange: (Boolean) -> Unit,
    onIncludeLogsChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appkit_feedback_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(AppTheme.spacing.lg),
        ) {
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.appkit_feedback_description)) },
                placeholder = { Text(stringResource(R.string.appkit_feedback_description_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = AppTheme.spacing.sm),
            ) {
                Checkbox(checked = includeScreenshot, onCheckedChange = onIncludeScreenshotChange)
                Text(stringResource(R.string.appkit_feedback_include_screenshot))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeLogs, onCheckedChange = onIncludeLogsChange)
                Text(stringResource(R.string.appkit_feedback_include_logs))
            }
            Button(
                onClick = onSend,
                modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.md),
            ) {
                Text(stringResource(R.string.appkit_feedback_send))
            }
        }
    }
}
