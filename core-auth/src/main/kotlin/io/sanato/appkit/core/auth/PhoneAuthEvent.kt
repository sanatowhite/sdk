package io.sanato.appkit.core.auth

/**
 * Opaque handle. Firebase's own `verificationId` is just a `String` under the
 * hood, but wrapping it makes "pass a random string where a verification id
 * is expected" a compile error, and leaves room to swap in a non-string
 * handle if a future provider needs one.
 */
@JvmInline
value class PhoneVerificationId(
    val value: String,
)

/**
 * Phone verification is structurally a multi-emission process — a suspend
 * function can't express it. The platform may call back with [CodeSent],
 * then later (or instead) with [AutoRetrieved] (same-device instant
 * verification), in an order that isn't guaranteed and isn't always both.
 * This is the one entry point in [AuthRepository] that doesn't return
 * `AppResult` — failure is a terminal event ([Failed]) instead; this `Flow`
 * never throws (`CancellationException` aside).
 */
sealed interface PhoneAuthEvent {
    /** SMS sent; the caller should collect a code from the user and call [AuthRepository.confirmPhoneVerificationCode]. */
    data class CodeSent(
        val verificationId: PhoneVerificationId,
    ) : PhoneAuthEvent

    /**
     * The platform completed verification on its own (same-device auto-read /
     * instant verification) and **already signed the user in**. There is no
     * code text to hand back in this path — UI observing this event should
     * navigate away immediately rather than wait for manual code entry.
     */
    data class AutoRetrieved(
        val user: AuthUser,
    ) : PhoneAuthEvent

    /**
     * The auto-retrieval window elapsed (60s by default). Not an error —
     * [CodeSent] already fired; this is the normal "now wait for manual
     * entry" signal.
     */
    data object AutoRetrievalTimeout : PhoneAuthEvent

    /** Terminal — the `Flow` completes immediately after this. */
    data class Failed(
        val error: AuthError,
    ) : PhoneAuthEvent
}
