package io.sanato.appkit.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.sanato.appkit.core.auth.AuthProvider
import io.sanato.appkit.core.ui.components.AppScaffold
import io.sanato.appkit.core.ui.theme.AppTheme

/**
 * 无状态版本——所有数据/回调都是参数,不碰 `hiltViewModel()`,可以直接用于
 * Roborazzi 截图测试,也是不用 Hilt 的消费方唯一需要用到的入口。同
 * `SettingsScreen` 的定位。
 *
 * [providers] 是 [AuthPageConfig] 和运行期可用性([io.sanato.appkit.core.auth.AuthRepository.availableProviders])
 * 求交后的结果——本组件不需要知道 GMS 是否可用之类的判断逻辑。
 */
@Composable
fun SignInScreen(
    email: String,
    password: String,
    passwordVisible: Boolean,
    providers: List<AuthProvider>,
    submitEnabled: Boolean,
    inProgress: AuthProvider?,
    errorMessage: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onSubmitEmailPassword: () -> Unit,
    onProviderClick: (AuthProvider) -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToPhone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(modifier = modifier) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(AppTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.appkit_auth_signin_title), style = MaterialTheme.typography.headlineSmall)

            if (providers.contains(AuthProvider.Password)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text(stringResource(R.string.appkit_auth_email_label)) },
                    singleLine = true,
                    enabled = inProgress == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.appkit_auth_password_label)) },
                    singleLine = true,
                    enabled = inProgress == null,
                    visualTransformation =
                        if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    trailingIcon = {
                        // A text toggle, not an icon — Icons.Default.Visibility/VisibilityOff live in
                        // the extended icon set, not the `material-icons-core` this module depends on,
                        // and pulling in the extended artifact for one glyph isn't worth it.
                        TextButton(onClick = onTogglePasswordVisible) {
                            Text(
                                if (passwordVisible) {
                                    stringResource(R.string.appkit_auth_hide_password)
                                } else {
                                    stringResource(R.string.appkit_auth_show_password)
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                errorMessage?.let { message ->
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = onSubmitEmailPassword,
                    enabled = submitEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (inProgress == AuthProvider.Password) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.appkit_auth_signin_button))
                    }
                }

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onNavigateToSignUp, enabled = inProgress == null) {
                        Text(stringResource(R.string.appkit_auth_signup_link))
                    }
                    TextButton(onClick = onNavigateToForgotPassword, enabled = inProgress == null) {
                        Text(stringResource(R.string.appkit_auth_forgot_password_link))
                    }
                }
            } else {
                errorMessage?.let { message ->
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }
            }

            val socialProviders = providers.filter { it == AuthProvider.Google || it == AuthProvider.Apple }
            if (socialProviders.isNotEmpty()) {
                HorizontalDivider()
                socialProviders.forEach { provider ->
                    OutlinedButton(
                        onClick = { onProviderClick(provider) },
                        enabled = inProgress == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (inProgress == provider) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(providerButtonLabel(provider))
                        }
                    }
                }
            }

            if (providers.contains(AuthProvider.Phone)) {
                TextButton(onClick = onNavigateToPhone, enabled = inProgress == null) {
                    Text(stringResource(R.string.appkit_auth_phone_button))
                }
            }
        }
    }
}

@Composable
private fun providerButtonLabel(provider: AuthProvider): String =
    when (provider) {
        AuthProvider.Google -> stringResource(R.string.appkit_auth_google_button)
        AuthProvider.Apple -> stringResource(R.string.appkit_auth_apple_button)
        else -> provider.name
    }
