package io.sanato.apptemplate.whatsnew

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.sanato.apptemplate.R
import io.sanato.apptemplate.about.ChangelogReader
import io.sanato.apptemplate.core.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val latestEntry = remember { ChangelogReader.read(context).maxByOrNull { it.versionCode } }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(AppTheme.spacing.lg)) {
            Text(stringResource(R.string.whats_new_title), style = MaterialTheme.typography.titleLarge)
            latestEntry?.highlights?.forEach { highlight ->
                Text(
                    text = "• $highlight",
                    modifier = Modifier.padding(top = AppTheme.spacing.sm),
                )
            }
        }
    }
}
