package io.sanato.appkit.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.sanato.appkit.core.ui.components.AppScaffold
import io.sanato.appkit.core.ui.theme.AppTheme

/**
 * [resendSecondsRemaining] is a parameter computed by the ViewModel, not a
 * `LaunchedEffect` counting down inside this `@Composable` — the latter would
 * make this screen's Roborazzi baseline drift depending on exactly when the
 * screenshot test happens to sample it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneCodeScreen(
    phoneNumberMasked: String,
    code: String,
    codeLength: Int,
    verifying: Boolean,
    resendSecondsRemaining: Int,
    errorMessage: String?,
    onCodeChange: (String) -> Unit,
    onResend: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appkit_auth_phone_code_title)) },
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
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.appkit_auth_phone_code_body, phoneNumberMasked))

            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text(stringResource(R.string.appkit_auth_code_label)) },
                singleLine = true,
                enabled = !verifying,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (verifying) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }

            errorMessage?.let { message -> Text(text = message, color = MaterialTheme.colorScheme.error) }

            TextButton(onClick = onResend, enabled = resendSecondsRemaining == 0) {
                Text(
                    if (resendSecondsRemaining > 0) {
                        stringResource(R.string.appkit_auth_resend_code_countdown, resendSecondsRemaining)
                    } else {
                        stringResource(R.string.appkit_auth_resend_code_button)
                    },
                )
            }
        }
    }
}
