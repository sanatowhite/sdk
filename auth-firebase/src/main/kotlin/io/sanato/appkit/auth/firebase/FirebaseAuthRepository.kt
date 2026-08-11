package io.sanato.appkit.auth.firebase

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import io.sanato.appkit.core.auth.AuthError
import io.sanato.appkit.core.auth.AuthProvider
import io.sanato.appkit.core.auth.AuthRepository
import io.sanato.appkit.core.auth.AuthState
import io.sanato.appkit.core.auth.AuthTokenProvider
import io.sanato.appkit.core.auth.AuthUser
import io.sanato.appkit.core.auth.PhoneAuthEvent
import io.sanato.appkit.core.auth.PhoneVerificationId
import io.sanato.appkit.core.auth.SignOutReason
import io.sanato.appkit.core.common.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * The sole implementation of [AuthRepository]/[AuthTokenProvider] shipped by
 * this SDK. Every public method's signature is checked against ADR 0011's
 * four conditions in `README.md`; the short version is: no Firebase/GMS/
 * Credential-Manager type ever appears in a public signature here, and the
 * only place [com.google.firebase.auth.FirebaseUser] is touched is
 * [toAuthUser].
 *
 * `FirebaseAuth.getInstance()` is called lazily inside each method rather
 * than injected as a constructor parameter — a process with no
 * `google-services.json` (consumer-smoke, or a fork mid-setup) must be able
 * to construct this class and even bind it via Hilt without crashing; the
 * crash, if any, should only happen the moment someone actually tries to
 * sign in.
 */
class FirebaseAuthRepository(
    private val context: Context,
    private val googleWebClientId: String?,
    private val appleScopes: List<String> = listOf("email", "name"),
    private val externalScope: CoroutineScope,
) : AuthRepository,
    AuthTokenProvider {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /**
     * Kept fresh by [FirebaseAuth.AuthStateListener]/[FirebaseAuth.IdTokenListener]
     * rather than fetched on demand — this is what lets [cachedIdToken] make
     * good on its "never blocks, never calls the network" contract.
     */
    @Volatile private var cachedToken: String? = null

    private var lastSignOutReason: SignOutReason = SignOutReason.NeverSignedIn

    /** [PhoneAuthProvider.ForceResendingToken] never leaves this module — resend is keyed by our own [PhoneVerificationId]. */
    private val resendTokens = LinkedHashMap<String, PhoneAuthProvider.ForceResendingToken>()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _authState.value =
                firebaseAuth.currentUser?.let { AuthState.SignedIn(it.toAuthUser()) }
                    ?: AuthState.SignedOut(lastSignOutReason)
        }
        // Explicit anonymous object, not a SAM-converted lambda: `FirebaseAuth.IdTokenListener`'s
        // single method carries a checker-framework type-use annotation
        // (`@UnknownInitialization`) that isn't on this module's classpath, which trips up
        // Kotlin's SAM-lambda type inference ("inferred type is inaccessible"). Declaring the
        // parameter type ourselves sidesteps that inference path entirely.
        auth.addIdTokenListener(
            object : FirebaseAuth.IdTokenListener {
                override fun onIdTokenChanged(firebaseAuth: FirebaseAuth) {
                    val user = firebaseAuth.currentUser
                    if (user == null) {
                        cachedToken = null
                    } else {
                        externalScope.launch {
                            cachedToken = runCatching { user.getIdToken(false).await().token }.getOrNull()
                        }
                    }
                }
            },
        )
    }

    // ── Email + password ─────────────────────────────────────────────

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AppResult<AuthUser> =
        runCatchingAuth {
            auth
                .signInWithEmailAndPassword(email, password)
                .await()
                .user!!
                .toAuthUser()
        }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
    ): AppResult<AuthUser> =
        runCatchingAuth {
            auth
                .createUserWithEmailAndPassword(email, password)
                .await()
                .user!!
                .toAuthUser()
        }

    override suspend fun sendPasswordResetEmail(email: String): AppResult<Unit> =
        runCatchingAuth<Unit> { auth.sendPasswordResetEmail(email).await() }

    override suspend fun sendEmailVerification(): AppResult<Unit> =
        runCatchingAuth<Unit> {
            val user = auth.currentUser ?: throw AuthError.NotSignedIn()
            user.sendEmailVerification().await()
        }

    override suspend fun updatePassword(newPassword: String): AppResult<Unit> =
        runCatchingAuth<Unit> {
            val user = auth.currentUser ?: throw AuthError.NotSignedIn()
            user.updatePassword(newPassword).await()
        }

    // ── Google ────────────────────────────────────────────────────────

    override suspend fun signInWithGoogle(activity: Activity): AppResult<AuthUser> =
        runCatchingAuth {
            val clientId = googleWebClientId ?: throw AuthError.ProviderNotEnabled(AuthProvider.Google)
            val option =
                GetSignInWithGoogleOption
                    .Builder(clientId)
                    .setNonce(randomNonce())
                    .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

            val response = CredentialManager.create(activity).getCredential(activity, request)

            val custom = response.credential as? CustomCredential ?: throw AuthError.NoCredentialAvailable()
            if (custom.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                throw AuthError.NoCredentialAvailable()
            }
            val idToken = GoogleIdTokenCredential.createFrom(custom.data).idToken

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth
                .signInWithCredential(credential)
                .await()
                .user!!
                .toAuthUser()
        }

    // ── Apple ─────────────────────────────────────────────────────────

    override suspend fun signInWithApple(activity: Activity): AppResult<AuthUser> =
        runCatchingAuth {
            // Process death mid-flow: pick up the pending result instead of
            // re-launching a second Custom Tab on top of whatever's already there.
            auth.pendingAuthResult?.let { pending ->
                return@runCatchingAuth pending.await().user!!.toAuthUser()
            }
            val provider =
                OAuthProvider
                    .newBuilder(APPLE_PROVIDER_ID)
                    .setScopes(appleScopes)
                    .build()
            auth
                .startActivityForSignInWithProvider(activity, provider)
                .await()
                .user!!
                .toAuthUser()
        }

    // ── Phone (two-step) ─────────────────────────────────────────────

    override fun signInWithPhoneNumber(
        activity: Activity,
        phoneNumber: String,
        autoRetrievalTimeout: Duration,
    ): Flow<PhoneAuthEvent> = phoneAuthFlow(activity, phoneNumber, autoRetrievalTimeout, resendToken = null)

    override fun resendPhoneVerificationCode(
        activity: Activity,
        phoneNumber: String,
        previous: PhoneVerificationId,
        autoRetrievalTimeout: Duration,
    ): Flow<PhoneAuthEvent> =
        phoneAuthFlow(activity, phoneNumber, autoRetrievalTimeout, resendToken = resendTokens[previous.value])

    private fun phoneAuthFlow(
        activity: Activity,
        phoneNumber: String,
        timeout: Duration,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
    ): Flow<PhoneAuthEvent> =
        callbackFlow {
            val callbacks =
                object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onCodeSent(
                        verificationId: String,
                        token: PhoneAuthProvider.ForceResendingToken,
                    ) {
                        if (resendTokens.size >= MAX_TRACKED_RESEND_TOKENS) {
                            resendTokens.remove(resendTokens.keys.first())
                        }
                        resendTokens[verificationId] = token
                        trySend(PhoneAuthEvent.CodeSent(PhoneVerificationId(verificationId)))
                    }

                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        launch {
                            runCatchingAuth {
                                auth
                                    .signInWithCredential(credential)
                                    .await()
                                    .user!!
                                    .toAuthUser()
                            }.let { result ->
                                when (result) {
                                    is AppResult.Success -> {
                                        trySend(PhoneAuthEvent.AutoRetrieved(result.data))
                                    }

                                    is AppResult.Failure -> {
                                        trySend(PhoneAuthEvent.Failed(result.error as AuthError))
                                    }
                                }
                            }
                            close()
                        }
                    }

                    override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
                        trySend(PhoneAuthEvent.AutoRetrievalTimeout)
                        // Not terminal: CodeSent already fired, the user can still type the code manually.
                    }

                    override fun onVerificationFailed(exception: FirebaseException) {
                        trySend(PhoneAuthEvent.Failed(exception.toAuthError()))
                        close()
                    }
                }

            val options =
                PhoneAuthOptions
                    .newBuilder(auth)
                    .setPhoneNumber(phoneNumber)
                    .setTimeout(timeout.inWholeSeconds, TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setCallbacks(callbacks)
                    .apply { resendToken?.let(::setForceResendingToken) }
                    .build()

            PhoneAuthProvider.verifyPhoneNumber(options)

            awaitClose {
                // Firebase exposes no unregister API; the callbacks object becomes eligible for GC once this flow's collector goes away.
            }
        }

    override suspend fun confirmPhoneVerificationCode(
        verificationId: PhoneVerificationId,
        smsCode: String,
    ): AppResult<AuthUser> =
        runCatchingAuth {
            val credential = PhoneAuthProvider.getCredential(verificationId.value, smsCode)
            auth
                .signInWithCredential(credential)
                .await()
                .user!!
                .toAuthUser()
        }

    // ── Session lifecycle ─────────────────────────────────────────────

    override suspend fun signOut(): AppResult<Unit> =
        runCatchingAuth {
            lastSignOutReason = SignOutReason.UserInitiated
            auth.signOut()
        }

    override suspend fun deleteAccount(): AppResult<Unit> =
        runCatchingAuth<Unit> {
            val user = auth.currentUser ?: throw AuthError.NotSignedIn()
            lastSignOutReason = SignOutReason.AccountDeleted
            user.delete().await()
        }

    override suspend fun reauthenticateWithPassword(password: String): AppResult<Unit> =
        runCatchingAuth<Unit> {
            val user = auth.currentUser ?: throw AuthError.NotSignedIn()
            val email = user.email ?: throw AuthError.InvalidEmail()
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        }

    override suspend fun reloadUser(): AppResult<AuthUser> =
        runCatchingAuth {
            val user = auth.currentUser ?: throw AuthError.NotSignedIn()
            user.reload().await()
            user.toAuthUser()
        }

    override suspend fun availableProviders(): Set<AuthProvider> {
        val providers = mutableSetOf(AuthProvider.Password, AuthProvider.Apple, AuthProvider.Phone)
        if (googleWebClientId != null && isGooglePlayServicesAvailable()) providers += AuthProvider.Google
        return providers
    }

    private fun isGooglePlayServicesAvailable(): Boolean =
        runCatching {
            val availability =
                Class
                    .forName("com.google.android.gms.common.GoogleApiAvailability")
                    .getMethod("getInstance")
                    .invoke(null)
            val result =
                availability
                    ?.javaClass
                    ?.getMethod("isGooglePlayServicesAvailable", Context::class.java)
                    ?.invoke(availability, context) as? Int
            result == 0 // ConnectionResult.SUCCESS
        }.getOrDefault(false)

    // ── AuthTokenProvider ─────────────────────────────────────────────

    override suspend fun currentIdToken(forceRefresh: Boolean): String? =
        auth.currentUser
            ?.getIdToken(forceRefresh)
            ?.await()
            ?.token
            ?.also { cachedToken = it }

    override fun cachedIdToken(): String? = cachedToken

    override suspend fun invalidateToken() {
        cachedToken = null
    }

    private companion object {
        const val APPLE_PROVIDER_ID = "apple.com"
        const val MAX_TRACKED_RESEND_TOKENS = 8
    }
}

/**
 * The only place in this module that touches [FirebaseUser] — everywhere
 * else works with [AuthUser]. `private`, not `internal`: unlike
 * `toAuthProvider()`/`toAuthError()` (tested directly from
 * `FirebaseErrorMappingTest`, and vendor-type-free in their own signatures),
 * this function's parameter type *is* a vendor type — `internal` top-level
 * declarations still compile to a public JVM method with an unmangled name
 * (Kotlin's module-visibility check is source-level only), so this one has
 * to be `private`(file-scoped) to actually keep [FirebaseUser] out of
 * `auth-firebase.api`'s golden snapshot, honoring ADR 0011 condition 2.
 */
private fun FirebaseUser.toAuthUser(): AuthUser =
    AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString(),
        phoneNumber = phoneNumber,
        isEmailVerified = isEmailVerified,
        isAnonymous = isAnonymous,
        providers =
            providerData
                .map { it.providerId.toAuthProvider() }
                .filterNot { it == AuthProvider.Unknown && providerData.size > 1 }
                .toSet(),
        createdAtMillis = metadata?.creationTimestamp,
        lastSignInAtMillis = metadata?.lastSignInTimestamp,
    )

/**
 * SHA-256 hash of a random UUID, hex-encoded — Google's recommended nonce
 * shape for [GetSignInWithGoogleOption.Builder.setNonce]. Purely a replay
 * fingerprint: this SDK doesn't verify ID tokens client-side (Firebase does
 * signature/audience/expiry validation on `signInWithCredential`), so there's
 * no need to retain the pre-hash value for a later comparison.
 */
private fun randomNonce(): String {
    val raw = UUID.randomUUID().toString()
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
    return digest.joinToString(separator = "") { "%02x".format(it) }
}
