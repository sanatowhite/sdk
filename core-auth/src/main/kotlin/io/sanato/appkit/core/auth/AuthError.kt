package io.sanato.appkit.core.auth

/**
 * `:core-auth`'s equivalent of `:core-net`'s `AppError` — a domain error type
 * follows its owning capability module, it doesn't get promoted to
 * `:core-common`. Kept a `Throwable` subtype (not a plain enum/sealed value)
 * because `AppResult.Failure(error: Throwable)` requires it.
 */
sealed class AuthError(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class InvalidEmail(
        cause: Throwable? = null,
    ) : AuthError("Malformed email address", cause)

    /**
     * Wrong password, or invalid credentials generally.
     *
     * ⚠️ With Firebase's email-enumeration protection enabled (the default on
     * newer projects), both "wrong password" and "no such account" collapse
     * into this one error — UI copy must stay neutral ("email or password is
     * incorrect"), never "no account with that email" (wrong *and* a privacy leak).
     */
    class InvalidCredentials(
        cause: Throwable? = null,
    ) : AuthError("Invalid credentials", cause)

    /** Only surfaces on older projects that haven't enabled enumeration protection. */
    class UserNotFound(
        cause: Throwable? = null,
    ) : AuthError("No such account", cause)

    class UserDisabled(
        cause: Throwable? = null,
    ) : AuthError("Account disabled", cause)

    class EmailAlreadyInUse(
        val email: String?,
        cause: Throwable? = null,
    ) : AuthError("Email already in use", cause)

    class WeakPassword(
        val reason: String?,
        cause: Throwable? = null,
    ) : AuthError(reason ?: "Password too weak", cause)

    /**
     * This email is already claimed by a **different** sign-in method
     * (classic case: signed up with Google, then tries email+password with
     * the same address). [conflictId] is an opaque handle a UI layer can use
     * to drive an account-linking flow; [email] is for building copy like
     * "please sign in with Google instead."
     */
    class AccountExistsWithDifferentCredential(
        val email: String?,
        val conflictId: String,
        cause: Throwable? = null,
    ) : AuthError("Account exists with a different sign-in method", cause)

    /** The provider isn't enabled in the auth backend's console — the most common "why did nothing happen" after a fork. */
    class ProviderNotEnabled(
        val provider: AuthProvider,
        cause: Throwable? = null,
    ) : AuthError("Provider not enabled: $provider", cause)

    class InvalidPhoneNumber(
        cause: Throwable? = null,
    ) : AuthError("Malformed phone number", cause)

    class InvalidVerificationCode(
        cause: Throwable? = null,
    ) : AuthError("Wrong SMS code", cause)

    class VerificationCodeExpired(
        cause: Throwable? = null,
    ) : AuthError("SMS code expired", cause)

    /** User dismissed the Google/Apple sign-in UI. Never surface this as an error toast. */
    class Cancelled(
        cause: Throwable? = null,
    ) : AuthError("Cancelled by user", cause)

    /** No Google account available on-device, or the user declined Credential Manager's prompt. */
    class NoCredentialAvailable(
        cause: Throwable? = null,
    ) : AuthError("No credential available", cause)

    /** A sensitive operation (delete account / change password) requires a recent sign-in. */
    class RequiresRecentLogin(
        cause: Throwable? = null,
    ) : AuthError("Recent login required", cause)

    /** Called a method that requires a signed-in user, but there is none. */
    class NotSignedIn : AuthError("No signed-in user")

    class TooManyRequests(
        cause: Throwable? = null,
    ) : AuthError("Rate limited", cause)

    class Network(
        cause: Throwable? = null,
    ) : AuthError("Network unavailable", cause)

    /** [code] preserves the raw provider error code purely for diagnosability. */
    class Unknown(
        val code: String?,
        cause: Throwable? = null,
    ) : AuthError("Unhandled auth error: $code", cause)
}
