package io.sanato.appkit.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.sanato.appkit.core.ui.theme.AppTheme

/** 无状态版本——`latestEntry` 为 `null` 时调用方不应该展示这个弹窗。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(
    latestEntry: ChangelogEntry?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(AppTheme.spacing.lg)) {
            Text(stringResource(R.string.appkit_whats_new_title), style = MaterialTheme.typography.titleLarge)
            latestEntry?.highlights?.forEach { highlight ->
                Text(
                    text = "• $highlight",
                    modifier = Modifier.padding(top = AppTheme.spacing.sm),
                )
            }
        }
    }
}
