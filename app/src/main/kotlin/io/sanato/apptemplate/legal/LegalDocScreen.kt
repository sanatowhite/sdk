package io.sanato.apptemplate.legal

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.sanato.apptemplate.core.ui.components.AppScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocScreen(
    title: String,
    @RawRes rawResId: Int,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val content =
        remember(rawResId) {
            context.resources
                .openRawResource(rawResId)
                .bufferedReader()
                .use { it.readText() }
        }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        MarkdownDocument(markdown = content, modifier = Modifier.fillMaxSize().padding(padding))
    }
}
