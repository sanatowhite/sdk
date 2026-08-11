package io.sanato.appkit.download

/**
 * `:downloadkit`'s equivalent of `:core-net`'s `AppError` / `:core-auth`'s
 * `AuthError` — a domain error type follows its owning capability module, it
 * doesn't get promoted to `:core-common`. Kept a `Throwable` subtype so it can
 * be thrown from [DownloadState.Failed] call sites and logged with a stack trace.
 */
sealed class DownloadError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Http(
        val code: Int,
    ) : DownloadError("HTTP $code")

    class Network(
        cause: Throwable,
    ) : DownloadError("network error", cause)

    class Io(
        cause: Throwable,
    ) : DownloadError("disk I/O error", cause)

    /**
     * The server's response for a resumed request didn't match what was
     * expected — e.g. a `200` where a `206` was required, or a `Content-Range`
     * whose start offset disagrees with the requested `Range`. Surfacing this
     * as its own error (rather than silently restarting) makes the "server
     * doesn't actually support range requests, or the remote file changed
     * mid-download" case debuggable instead of a silent full re-download.
     */
    class UnexpectedResponse(
        val httpCode: Int,
        detail: String,
    ) : DownloadError("unexpected response for resume: HTTP $httpCode ($detail)")

    class ChecksumMismatch(
        val expected: String,
        val actual: String,
    ) : DownloadError("sha256 mismatch: expected=$expected actual=$actual")

    class Canceled : DownloadError("canceled by caller")

    class MaxRetriesExceeded(
        val attempts: Int,
        cause: Throwable?,
    ) : DownloadError("gave up after $attempts attempts", cause)

    class Unknown(
        cause: Throwable,
    ) : DownloadError(cause.message ?: "unknown error", cause)
}
