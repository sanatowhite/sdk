package io.sanato.appkit.feature.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.sanato.appkit.core.auth.AuthProvider

/**
 * Hilt 消费方的入口——包一层 `hiltViewModel()`,把 [SignInScreen] 需要的状态
 * 从 [SignInViewModel] 里取出来。不用 Hilt 就直接用 [SignInScreen]。
 *
 * [config] 求交发生在这一层(纯函数,不需要 Hilt assisted injection):
 * `ViewModel` 已经从 `AuthRepository.availableProviders()` 拿到"运行期真的
 * 能用"的 provider 集合(比如 GMS 缺失时没有 Google),这里再和"消费方想不想
 * 展示"的 [config] 求交,两边都要素 provider 才会显示。
 */
@Composable
fun SignInRoute(
    onSignedIn: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToPhone: () -> Unit,
    config: AuthPageConfig = AuthPageConfig(),
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // androidx.activity.compose.LocalActivity, not `LocalContext.current as Activity` — this
    // repo's MainActivity is an AppCompatActivity, whose per-app-locale ContextWrapper makes
    // that cast crash on some Android versions.
    val activity = LocalActivity.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SignInEvent.SignedIn -> onSignedIn()
            }
        }
    }

    val enabledProviders =
        uiState.availableProviders.filter { provider ->
            when (provider) {
                AuthProvider.Password -> config.emailPasswordEnabled
                AuthProvider.Google -> config.googleEnabled
                AuthProvider.Apple -> config.appleEnabled
                AuthProvider.Phone -> config.phoneEnabled
                else -> true
            }
        }

    SignInScreen(
        email = uiState.email,
        password = uiState.password,
        passwordVisible = uiState.passwordVisible,
        providers = enabledProviders,
        submitEnabled = uiState.submitEnabled,
        inProgress = uiState.inProgress,
        errorMessage = uiState.error?.let { authErrorMessage(it) },
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePasswordVisible = viewModel::onTogglePasswordVisible,
        onSubmitEmailPassword = viewModel::signInWithEmailPassword,
        onProviderClick = { provider -> activity?.let { viewModel.signInWithProvider(provider, it) } },
        onNavigateToSignUp = onNavigateToSignUp,
        onNavigateToForgotPassword = onNavigateToForgotPassword,
        onNavigateToPhone = onNavigateToPhone,
    )
}
