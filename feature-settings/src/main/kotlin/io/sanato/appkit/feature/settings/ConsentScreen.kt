package io.sanato.appkit.feature.settings

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
import io.sanato.appkit.core.ui.components.AppScaffold
import io.sanato.appkit.core.ui.theme.AppTheme

/**
 * 无状态版本——`onAccept` 由调用方负责持久化 consent version。
 *
 * 条件 startDestination(见 [AppEntryViewModel])。未同意时消费方的 Deferred
 * 初始化(遥测采集等)不应该跑——这套接线是"同意后才 setConsentVersion,之后
 * Telemetry 是否真正上报由用户自己在设置页里的遥测开关决定",两者叠加已经能
 * 保证未同意用户不会被采集。
 */
@Composable
fun ConsentScreen(
    onAccept: () -> Unit,
    onViewPrivacyPolicy: () -> Unit,
    onViewTermsOfService: () -> Unit,
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
                text = stringResource(R.string.appkit_consent_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.appkit_consent_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onViewPrivacyPolicy) {
                Text(stringResource(R.string.appkit_settings_privacy_policy))
            }
            TextButton(onClick = onViewTermsOfService) {
                Text(stringResource(R.string.appkit_settings_terms_of_service))
            }
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.appkit_consent_accept))
            }
        }
    }
}
