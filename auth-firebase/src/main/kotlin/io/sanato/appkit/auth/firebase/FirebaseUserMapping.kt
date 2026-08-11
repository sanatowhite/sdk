package io.sanato.appkit.auth.firebase

import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthProvider
import io.sanato.appkit.core.auth.AuthProvider

/**
 * `internal` (not `private`) because [FirebaseErrorMappingTest] exercises it
 * directly — safe to share across this module's files since, unlike
 * [FirebaseAuthRepository]'s `toAuthUser()`, no vendor type ever appears in
 * this function's own signature (`String` in, [AuthProvider] out).
 */
internal fun String.toAuthProvider(): AuthProvider =
    when (this) {
        "password" -> AuthProvider.Password

        GoogleAuthProvider.PROVIDER_ID -> AuthProvider.Google

        APPLE_PROVIDER_ID -> AuthProvider.Apple

        PhoneAuthProvider.PROVIDER_ID -> AuthProvider.Phone

        // "firebase" is a pseudo-provider Firebase itself injects into
        // providerData; it isn't a real sign-in method and must be filtered,
        // not surfaced as Unknown.
        FIREBASE_PSEUDO_PROVIDER_ID -> AuthProvider.Unknown

        else -> AuthProvider.Unknown
    }

private const val APPLE_PROVIDER_ID = "apple.com"
private const val FIREBASE_PSEUDO_PROVIDER_ID = "firebase"
