package io.sanato.appkit.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.sanato.appkit.core.auth.AuthError

/**
 * `AuthError` → 展示文案的唯一映射点。`Screen` 只收已经解析好的 `String?`,
 * 从不 `import AuthError`——这样截图测试可以直接传字面量,且 `Screen` 保持对
 * `:core-auth` 零依赖。
 *
 * [AuthError.Cancelled] 刻意不在这里处理:那是用户主动关掉 Google/Apple 弹窗,
 * 调用方应该把它当"什么也没发生"静默吞掉,不应该走到这个函数。
 */
@Composable
fun authErrorMessage(error: AuthError): String =
    when (error) {
        is AuthError.InvalidEmail -> {
            stringResource(R.string.appkit_auth_error_invalid_email)
        }

        is AuthError.InvalidCredentials -> {
            stringResource(R.string.appkit_auth_error_invalid_credentials)
        }

        is AuthError.UserNotFound -> {
            stringResource(R.string.appkit_auth_error_invalid_credentials)
        }

        is AuthError.UserDisabled -> {
            stringResource(R.string.appkit_auth_error_user_disabled)
        }

        is AuthError.EmailAlreadyInUse -> {
            stringResource(R.string.appkit_auth_error_email_in_use)
        }

        is AuthError.WeakPassword -> {
            stringResource(R.string.appkit_auth_error_weak_password)
        }

        is AuthError.AccountExistsWithDifferentCredential -> {
            stringResource(R.string.appkit_auth_error_account_exists_different_credential)
        }

        is AuthError.ProviderNotEnabled -> {
            stringResource(R.string.appkit_auth_error_provider_not_enabled)
        }

        is AuthError.InvalidPhoneNumber -> {
            stringResource(R.string.appkit_auth_error_invalid_phone_number)
        }

        is AuthError.InvalidVerificationCode -> {
            stringResource(R.string.appkit_auth_error_invalid_verification_code)
        }

        is AuthError.VerificationCodeExpired -> {
            stringResource(R.string.appkit_auth_error_verification_code_expired)
        }

        is AuthError.Cancelled -> {
            ""
        }

        // See KDoc — callers should never reach this branch for a visible message.
        is AuthError.NoCredentialAvailable -> {
            stringResource(R.string.appkit_auth_error_no_credential_available)
        }

        is AuthError.RequiresRecentLogin -> {
            stringResource(R.string.appkit_auth_error_requires_recent_login)
        }

        is AuthError.NotSignedIn -> {
            stringResource(R.string.appkit_auth_error_not_signed_in)
        }

        is AuthError.TooManyRequests -> {
            stringResource(R.string.appkit_auth_error_too_many_requests)
        }

        is AuthError.Network -> {
            stringResource(R.string.appkit_auth_error_network)
        }

        is AuthError.Unknown -> {
            stringResource(R.string.appkit_auth_error_unknown)
        }
    }
