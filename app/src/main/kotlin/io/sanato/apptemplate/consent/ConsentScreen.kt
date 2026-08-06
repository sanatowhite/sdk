package io.sanato.apptemplate.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.sanato.apptemplate.R
import io.sanato.apptemplate.core.ui.components.AppScaffold
import io.sanato.apptemplate.core.ui.theme.AppTheme

/**
 * 条件 startDestination(见 `AppEntryViewModel`)。未同意时 `:app` 的 Deferred
 * 初始化(遥测采集等)不应该跑——本模板目前的接线是"同意后才 setConsentVersion,
 * 之后 Telemetry 是否真正上报由用户自己在设置页里的遥测开关决定",两者叠加
 * 已经能保证未同意用户不会被采集。
 */
@Composable
fun ConsentScreen(
    onAccepted: () -> Unit,
    onViewPrivacyPolicy: () -> Unit,
    onViewTermsOfService: () -> Unit,
    viewModel: ConsentViewModel = hiltViewModel(),
) {
    AppScaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(AppTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md, Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(R.string.consent_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.consent_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onViewPrivacyPolicy) {
                Text(stringResource(R.string.settings_privacy_policy))
            }
            TextButton(onClick = onViewTermsOfService) {
                Text(stringResource(R.string.settings_terms_of_service))
            }
            Button(
                onClick = { viewModel.accept(onAccepted) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.consent_accept))
            }
        }
    }
}
