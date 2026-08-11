package io.sanato.appkit.core.auth

/**
 * A signed-in identity, provider-agnostic — Firebase is only one possible
 * implementation behind [AuthRepository]. Deliberately carries no token: a
 * token's lifecycle (hourly expiry, needs refreshing) is nothing like this
 * immutable snapshot's, so it lives in [AuthTokenProvider] instead.
 */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val phoneNumber: String?,
    val isEmailVerified: Boolean,
    /**
     * This template doesn't provide a `signInAnonymously()` entry point, but
     * Firebase's console can enable anonymous auth independently, and any
     * existing anonymous session would otherwise show up here indistinguishable
     * from a real one — this field is what lets a consumer tell the difference.
     */
    val isAnonymous: Boolean,
    /** One account can link multiple providers (email + Google + phone), hence a `Set`. */
    val providers: Set<AuthProvider>,
    val createdAtMillis: Long?,
    val lastSignInAtMillis: Long?,
)

enum class AuthProvider {
    Password,
    Google,
    Apple,
    Phone,
    Anonymous,

    /** Unrecognized provider id — never throw, stay forward-compatible with new providers. */
    Unknown,
}
