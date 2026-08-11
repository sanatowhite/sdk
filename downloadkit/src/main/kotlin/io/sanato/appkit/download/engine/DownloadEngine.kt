package io.sanato.appkit.download.engine

import java.io.File

/**
 * Everything the engine needs to know to build the resume request — mirrors
 * what's persisted in `store/TaskMetadata.kt`. [bytesDownloaded] must equal
 * `destination.length()` before calling [DownloadEngine.download]; the engine
 * trusts it rather than re-`stat`-ing the file itself.
 */
internal data class ResumeInfo(
    val bytesDownloaded: Long,
    val etag: String?,
    val lastModified: String?,
    val acceptRanges: Boolean,
)

/**
 * What the server told us once response headers arrived — reported via
 * [DownloadEngine.download]'s `onHeaders` callback *before* any body bytes
 * are written, so these validators are captured even if the body transfer
 * itself later fails partway through (a dropped connection mid-stream still
 * leaves the caller with the right [io.sanato.appkit.download.store.TaskMetadata]
 * to resume from on the next attempt).
 */
internal data class ResponseInfo(
    val totalBytes: Long,
    val etag: String?,
    val lastModified: String?,
    val acceptRanges: Boolean,
)

/**
 * Internal seam between `queue/DownloadQueue.kt`'s retry loop and the real
 * HTTP client — same role `ws.WebSocketTransport` plays for the WebSocket
 * state machine. Never part of the published API (`internal`, no golden
 * entry); its only purpose is letting engine tests run against
 * [okhttp3.mockwebserver.MockWebServer] instead of a live server.
 *
 * One call = one HTTP exchange (one attempt). It does **not** retry on
 * failure — that's [io.sanato.appkit.download.queue.DownloadQueue]'s job, with
 * its own backoff policy. On success this returns normally and `destination`
 * holds the complete file. On failure it throws
 * [io.sanato.appkit.download.DownloadError] and `destination` holds whatever
 * prefix made it to disk — the caller re-derives the next [ResumeInfo] from
 * `destination.length()`.
 */
internal fun interface DownloadEngine {
    suspend fun download(
        url: String,
        headers: Map<String, String>,
        destination: File,
        resume: ResumeInfo,
        onHeaders: (ResponseInfo) -> Unit,
        onProgress: (bytesDownloaded: Long) -> Unit,
    )
}
