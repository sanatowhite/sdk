package io.sanato.appkit.core.auth

import android.app.Activity
import io.sanato.appkit.core.common.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration

/**
 * Pure in-memory fake for ViewModel/UI unit tests — mirrors `:core-data`'s
 * `FakeUserSettingsRepository`, the only other module in this repo whose
 * `testFixtures` are actually enabled and published. `Activity` parameters
 * are always ignored.
 */
class FakeAuthRepository(
    initial: AuthState = AuthState.SignedOut(),
) : AuthRepository,
    AuthTokenProvider {
    private val _authState = MutableStateFlow(initial)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** Drives every `signIn*`/`confirm*` call below. Set it before invoking the method under test. */
    var nextResult: AppResult<AuthUser> = AppResult.Failure(AuthError.NotSignedIn())

    var nextUnitResult: AppResult<Unit> = AppResult.Success(Unit)

    var nextPhoneEvents: List<PhoneAuthEvent> = emptyList()

    var availableProviders: Set<AuthProvider> = AuthProvider.entries.toSet()

    var token: String? = null

    /** Test-only: push a new state directly, bypassing any sign-in flow. */
    fun setState(state: AuthState) {
        _authState.value = state
    }

    private fun applyResult(result: AppResult<AuthUser>): AppResult<AuthUser> {
        if (result is AppResult.Success) _authState.value = AuthState.SignedIn(result.data)
        return result
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AppResult<AuthUser> = applyResult(nextResult)

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
    ): AppResult<AuthUser> = applyResult(nextResult)

    override suspend fun sendPasswordResetEmail(email: String): AppResult<Unit> = nextUnitResult

    override suspend fun sendEmailVerification(): AppResult<Unit> = nextUnitResult

    override suspend fun updatePassword(newPassword: String): AppResult<Unit> = nextUnitResult

    override suspend fun signInWithGoogle(activity: Activity): AppResult<AuthUser> = applyResult(nextResult)

    override suspend fun signInWithApple(activity: Activity): AppResult<AuthUser> = applyResult(nextResult)

    override fun signInWithPhoneNumber(
        activity: Activity,
        phoneNumber: String,
        autoRetrievalTimeout: Duration,
    ): Flow<PhoneAuthEvent> = flowOf(*nextPhoneEvents.toTypedArray())

    override fun resendPhoneVerificationCode(
        activity: Activity,
        phoneNumber: String,
        previous: PhoneVerificationId,
        autoRetrievalTimeout: Duration,
    ): Flow<PhoneAuthEvent> = flowOf(*nextPhoneEvents.toTypedArray())

    override suspend fun confirmPhoneVerificationCode(
        verificationId: PhoneVerificationId,
        smsCode: String,
    ): AppResult<AuthUser> = applyResult(nextResult)

    override suspend fun signOut(): AppResult<Unit> {
        _authState.value = AuthState.SignedOut(SignOutReason.UserInitiated)
        return AppResult.Success(Unit)
    }

    override suspend fun deleteAccount(): AppResult<Unit> {
        if (nextUnitResult is AppResult.Success) {
            _authState.value = AuthState.SignedOut(SignOutReason.AccountDeleted)
        }
        return nextUnitResult
    }

    override suspend fun reauthenticateWithPassword(password: String): AppResult<Unit> = nextUnitResult

    override suspend fun reloadUser(): AppResult<AuthUser> = nextResult

    override suspend fun availableProviders(): Set<AuthProvider> = availableProviders

    override suspend fun currentIdToken(forceRefresh: Boolean): String? = token

    override fun cachedIdToken(): String? = token

    override suspend fun invalidateToken() {
        token = null
    }
}
