package io.sanato.appkit.auth.firebase

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import io.sanato.appkit.core.auth.AuthError
import io.sanato.appkit.core.auth.AuthProvider
import io.sanato.appkit.core.common.AppResult
import kotlin.coroutines.cancellation.CancellationException

/**
 * `FirebaseAuthException.errorCode` (a string) → [AuthError]. This is the one
 * place that knows Firebase's error vocabulary; everything above
 * [FirebaseAuthRepository] only ever sees [AuthError].
 */
internal fun Throwable.toAuthError(): AuthError =
    when (this) {
        is AuthError -> {
            this
        }

        is CancellationException -> {
            throw this
        }

        // never swallow cancellation.

        // ── Credential Manager (Google sign-in) ──────────────────────
        is GetCredentialCancellationException -> {
            AuthError.Cancelled(this)
        }

        is NoCredentialException -> {
            AuthError.NoCredentialAvailable(this)
        }

        is GetCredentialException -> {
            AuthError.Unknown(this.type, this)
        }

        is FirebaseNetworkException -> {
            AuthError.Network(this)
        }

        is FirebaseTooManyRequestsException -> {
            AuthError.TooManyRequests(this)
        }

        is FirebaseAuthUserCollisionException -> {
            when (errorCode) {
                ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL -> {
                    AuthError.AccountExistsWithDifferentCredential(email, conflictIdFor(email), this)
                }

                // ERROR_EMAIL_ALREADY_IN_USE / ERROR_CREDENTIAL_ALREADY_IN_USE
                else -> {
                    AuthError.EmailAlreadyInUse(email, this)
                }
            }
        }

        is FirebaseAuthWeakPasswordException -> {
            AuthError.WeakPassword(reason, this)
        }

        is FirebaseAuthException -> {
            when (errorCode) {
                "ERROR_INVALID_EMAIL" -> AuthError.InvalidEmail(this)
                "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> AuthError.InvalidCredentials(this)
                "ERROR_USER_NOT_FOUND" -> AuthError.UserNotFound(this)
                "ERROR_USER_DISABLED" -> AuthError.UserDisabled(this)
                "ERROR_WEAK_PASSWORD" -> AuthError.WeakPassword(null, this)
                "ERROR_REQUIRES_RECENT_LOGIN" -> AuthError.RequiresRecentLogin(this)
                "ERROR_OPERATION_NOT_ALLOWED" -> AuthError.ProviderNotEnabled(AuthProvider.Unknown, this)
                "ERROR_TOO_MANY_REQUESTS", "ERROR_QUOTA_EXCEEDED" -> AuthError.TooManyRequests(this)
                "ERROR_INVALID_PHONE_NUMBER", "ERROR_MISSING_PHONE_NUMBER" -> AuthError.InvalidPhoneNumber(this)
                "ERROR_INVALID_VERIFICATION_CODE" -> AuthError.InvalidVerificationCode(this)
                "ERROR_INVALID_VERIFICATION_ID", "ERROR_SESSION_EXPIRED" -> AuthError.VerificationCodeExpired(this)
                "ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN" -> AuthError.RequiresRecentLogin(this)
                "ERROR_WEB_CONTEXT_CANCELED" -> AuthError.Cancelled(this)
                else -> AuthError.Unknown(errorCode, this)
            }
        }

        else -> {
            AuthError.Unknown(null, this)
        }
    }

/**
 * Firebase doesn't hand back a stable identifier for "the conflict that just
 * happened" — [AuthError.AccountExistsWithDifferentCredential] needs
 * *something* a UI layer can round-trip through navigation state (see
 * `:core-auth`'s KDoc on why route params can't carry raw emails). Using the
 * email itself is deliberate: it's already the least-sensitive piece of data
 * in this whole error, and it lets a caller re-derive everything it needs
 * without this module having to hold onto any additional session state.
 */
private fun conflictIdFor(email: String?): String = email.orEmpty()

private const val ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL = "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL"

/**
 * `internal inline`, never `public inline`. Same reasoning as `:core-net`'s
 * `safeApiCall`: a public inline function copies its body (and therefore the
 * exact set of caught types) into every call site's bytecode, permanently
 * freezing it into consumers' ABI. Staying `internal` keeps this an
 * implementation detail this module can change freely — it's shared across
 * this module's own files, but invisible outside `:auth-firebase`.
 */
internal inline fun <T> runCatchingAuth(block: () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppResult.Failure(e.toAuthError())
    }
