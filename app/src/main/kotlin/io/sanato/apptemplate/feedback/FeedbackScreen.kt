package io.sanato.apptemplate.feedback

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.sanato.apptemplate.R
import io.sanato.apptemplate.core.ui.components.AppScaffold
import io.sanato.apptemplate.core.ui.theme.AppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onNavigateBack: () -> Unit,
    viewModel: FeedbackViewModel = hiltViewModel(),
) {
    var description by remember { mutableStateOf("") }
    var includeScreenshot by remember { mutableStateOf(true) }
    var includeLogs by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feedback_title)) },
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
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.feedback_description)) },
                placeholder = { Text(stringResource(R.string.feedback_description_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = AppTheme.spacing.sm),
            ) {
                Checkbox(checked = includeScreenshot, onCheckedChange = { includeScreenshot = it })
                Text(stringResource(R.string.feedback_include_screenshot))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeLogs, onCheckedChange = { includeLogs = it })
                Text(stringResource(R.string.feedback_include_logs))
            }
            Button(
                onClick = {
                    scope.launch {
                        val screenshot = if (includeScreenshot) AppScreenshot.capture()?.asAndroidBitmap() else null
                        viewModel.sendFeedback(description, screenshot, includeLogs)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.md),
            ) {
                Text(stringResource(R.string.feedback_send))
            }
        }
    }
}
