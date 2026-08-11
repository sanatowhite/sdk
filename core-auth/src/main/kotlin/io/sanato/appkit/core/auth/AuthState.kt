package io.sanato.appkit.core.auth

/**
 * [Unknown] is the entire reason this type exists: on cold start, "haven't
 * read the session yet" and "confirmed signed out" must be distinguishable,
 * or the start destination flashes a sign-in screen for one frame while a
 * persisted session is still being restored. This mirrors the existing
 * `AppEntryViewModel.consentRequired: StateFlow<Boolean?>` pattern
 * (`:feature-settings`) — `null` there plays the same role [Unknown] plays here.
 */
sealed interface AuthState {
    data object Unknown : AuthState

    data class SignedOut(
        val reason: SignOutReason = SignOutReason.NeverSignedIn,
    ) : AuthState

    data class SignedIn(
        val user: AuthUser,
    ) : AuthState
}

enum class SignOutReason {
    UserInitiated,
    SessionExpired,
    AccountDisabled,
    AccountDeleted,
    NeverSignedIn,
}
