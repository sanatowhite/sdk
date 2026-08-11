package io.sanato.appkit.core.auth

import android.app.Activity
import io.sanato.appkit.core.common.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Provider-agnostic identity/session contract. Firebase (`:auth-firebase`) is
 * the only implementation shipped by this SDK, but nothing here mentions it —
 * a consumer could write a custom-backend implementation without touching
 * this interface.
 *
 * All results use `AppResult<T>` (from `:core-common`), not thrown exceptions
 * — the same convention `:core-net`'s `safeApiCall` established for this
 * repo. [AuthError] is the `Throwable` that ends up in `AppResult.Failure`.
 */
interface AuthRepository {
    /** Cold-start's initial value must be [AuthState.Unknown]. */
    val authState: StateFlow<AuthState>

    // ── Email + password ─────────────────────────────────────────────
    suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AppResult<AuthUser>

    suspend fun signUpWithEmail(
        email: String,
        password: String,
    ): AppResult<AuthUser>

    suspend fun sendPasswordResetEmail(email: String): AppResult<Unit>

    suspend fun sendEmailVerification(): AppResult<Unit>

    /** May fail with [AuthError.RequiresRecentLogin] — see that error's KDoc for the recovery path. */
    suspend fun updatePassword(newPassword: String): AppResult<Unit>

    // ── Google / Apple ────────────────────────────────────────────────

    /**
     * @param activity Must be a foreground `Activity` — Credential Manager's
     *   account-picker bottom sheet anchors to it. In Compose, obtain it via
     *   `androidx.activity.compose.LocalActivity`. Pass it as a parameter
     *   only, never store it — see `:feature-auth`'s ViewModel KDoc for why
     *   that's safe here.
     */
    suspend fun signInWithGoogle(activity: Activity): AppResult<AuthUser>

    /** Apple sign-in is a web OAuth flow (Custom Tab) even on Android; it needs a foreground `Activity` for the same reason as [signInWithGoogle]. */
    suspend fun signInWithApple(activity: Activity): AppResult<AuthUser>

    // ── Phone SMS verification (two-step) ────────────────────────────

    /**
     * @param phoneNumber Must be E.164 (`"+14155552671"`) — this interface
     *   does no formatting.
     * @param autoRetrievalTimeout Window for same-device auto-read; after it
     *   elapses, [PhoneAuthEvent.AutoRetrievalTimeout] is emitted.
     */
    fun signInWithPhoneNumber(
        activity: Activity,
        phoneNumber: String,
        autoRetrievalTimeout: Duration = DEFAULT_PHONE_AUTO_RETRIEVAL_TIMEOUT,
    ): Flow<PhoneAuthEvent>

    /** [previous] lets implementations reuse a platform resend token where available, so the user isn't rate-limited as if this were a brand-new session. */
    fun resendPhoneVerificationCode(
        activity: Activity,
        phoneNumber: String,
        previous: PhoneVerificationId,
        autoRetrievalTimeout: Duration = DEFAULT_PHONE_AUTO_RETRIEVAL_TIMEOUT,
    ): Flow<PhoneAuthEvent>

    suspend fun confirmPhoneVerificationCode(
        verificationId: PhoneVerificationId,
        smsCode: String,
    ): AppResult<AuthUser>

    // ── Session lifecycle ─────────────────────────────────────────────
    suspend fun signOut(): AppResult<Unit>

    /**
     * Deletes the account. The common failure is [AuthError.RequiresRecentLogin]
     * — the recovery path is having the user sign in again with whatever
     * method they already used and retrying, not a dedicated set of
     * `reauthenticate*` methods (which would nearly duplicate `signInWith*`).
     * The one exception is password accounts — see [reauthenticateWithPassword].
     */
    suspend fun deleteAccount(): AppResult<Unit>

    /**
     * Password-account-only reauthentication. Re-running [signInWithEmail]
     * would risk silently switching to a *different* account if the email is
     * mistyped; this keeps the current account fixed while just re-verifying
     * the password.
     */
    suspend fun reauthenticateWithPassword(password: String): AppResult<Unit>

    /** Forces a fresh pull of the user's profile (email-verified state, display name, etc.) from the backend. */
    suspend fun reloadUser(): AppResult<AuthUser>

    /** Which providers can actually be offered right now — e.g. Google is dropped when GMS is unavailable or unconfigured. */
    suspend fun availableProviders(): Set<AuthProvider>

    private companion object {
        val DEFAULT_PHONE_AUTO_RETRIEVAL_TIMEOUT = 60.seconds
    }
}
