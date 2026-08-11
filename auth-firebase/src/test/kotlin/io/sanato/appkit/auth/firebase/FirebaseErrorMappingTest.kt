package io.sanato.appkit.auth.firebase

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import io.sanato.appkit.core.auth.AuthError
import io.sanato.appkit.core.auth.AuthProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The mapping logic itself needs no `FirebaseApp`/`google-services.json`, but
 * merely *constructing* a real `FirebaseAuthException`/`FirebaseNetworkException`
 * touches `android.text.TextUtils` internally, which AGP's default unit-test
 * stub jar throws on — hence `@RunWith(AndroidJUnit4::class)` + Robolectric
 * here, unlike the rest of this repo's plain-JVM unit tests.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseErrorMappingTest {
    @Test
    fun `maps known FirebaseAuthException error codes`() {
        assertTrue(FirebaseAuthException("ERROR_INVALID_EMAIL", "msg").toAuthError() is AuthError.InvalidEmail)
        assertTrue(FirebaseAuthException("ERROR_WRONG_PASSWORD", "msg").toAuthError() is AuthError.InvalidCredentials)
        assertTrue(
            FirebaseAuthException("ERROR_INVALID_PHONE_NUMBER", "msg").toAuthError() is AuthError.InvalidPhoneNumber,
        )
        assertTrue(
            FirebaseAuthException(
                "ERROR_INVALID_VERIFICATION_CODE",
                "msg",
            ).toAuthError() is AuthError.InvalidVerificationCode,
        )
    }

    @Test
    fun `unrecognized error code falls back to Unknown but preserves the raw code`() {
        val error = FirebaseAuthException("ERROR_SOMETHING_NEW", "msg").toAuthError()
        assertTrue(error is AuthError.Unknown)
        assertEquals("ERROR_SOMETHING_NEW", (error as AuthError.Unknown).code)
    }

    @Test
    fun `network and rate-limit exceptions map to their dedicated errors`() {
        assertTrue(FirebaseNetworkException("offline").toAuthError() is AuthError.Network)
        assertTrue(FirebaseTooManyRequestsException("slow down").toAuthError() is AuthError.TooManyRequests)
    }

    @Test
    fun `credential manager cancellation and no-credential map without crashing`() {
        assertTrue(GetCredentialCancellationException("cancelled").toAuthError() is AuthError.Cancelled)
        assertTrue(NoCredentialException("none").toAuthError() is AuthError.NoCredentialAvailable)
    }

    @Test
    fun `a plain AuthError passed through toAuthError is returned unchanged`() {
        val original = AuthError.NotSignedIn()
        assertEquals(original, original.toAuthError())
    }

    @Test
    fun `known Firebase provider ids map to the right AuthProvider`() {
        assertEquals(AuthProvider.Password, "password".toAuthProvider())
        assertEquals(AuthProvider.Google, "google.com".toAuthProvider())
        assertEquals(AuthProvider.Apple, "apple.com".toAuthProvider())
        assertEquals(AuthProvider.Phone, "phone".toAuthProvider())
        assertEquals(AuthProvider.Unknown, "firebase".toAuthProvider())
        assertEquals(AuthProvider.Unknown, "some-future-provider.com".toAuthProvider())
    }
}
