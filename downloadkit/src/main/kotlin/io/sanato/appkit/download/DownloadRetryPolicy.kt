package io.sanato.appkit.download

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Same shape as `:core-net`'s `ws.WebSocketRetryPolicy` (exponential backoff +
 * jitter + a hard cap), minus `resetAfterConnectedFor` — that field exists to
 * distinguish "connected briefly then dropped" from "never connected" for a
 * *long-lived* connection. A download attempt is a finite, bounded operation:
 * there is no equivalent "was healthy for a while" signal to reset on.
 */
data class DownloadRetryPolicy(
    val maxAttempts: Int = 5,
    val initialDelay: Duration = 1.seconds,
    val maxDelay: Duration = 30.seconds,
    val multiplier: Double = 2.0,
    /** Actual delay = base × (1 ± jitterRatio × random). Avoids thundering-herd resumes after a shared outage. */
    val jitterRatio: Double = 0.2,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
    }
}
