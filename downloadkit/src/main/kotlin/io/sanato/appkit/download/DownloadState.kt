package io.sanato.appkit.download

import java.io.File

/**
 * `sealed interface` rather than an enum + separate progress fields — same
 * reasoning as `:core-net`'s `ws.WebSocketState`: [Failed] must carry *why*,
 * [Running]/[Paused] must carry *how far*, an enum can't do either.
 */
sealed interface DownloadState {
    /** Persisted, not yet handed to the engine — waiting for a queue slot. */
    data object Queued : DownloadState

    data class Running(
        val bytesDownloaded: Long,
        /** `-1L` until the server tells us via `Content-Range`'s denominator. */
        val totalBytes: Long,
    ) : DownloadState {
        /** `-1f` while [totalBytes] is still unknown — never divide-by-zero. */
        val progress: Float get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else -1f
    }

    /** Stopped by the caller, the process dying, or a retryable failure — resumable via [Downloader.resume]. */
    data class Paused(
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : DownloadState

    data class Completed(
        val file: File,
        val totalBytes: Long,
    ) : DownloadState

    /** Retries exhausted, or a non-retryable [DownloadError]. The `.part` file is preserved for a manual [Downloader.resume]. */
    data class Failed(
        val error: DownloadError,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : DownloadState

    /** Terminal. `.part`/`.meta` are deleted — unlike [Failed]/[Paused], there is nothing left to resume. */
    data object Canceled : DownloadState
}

/**
 * A task's identity ([id]) plus its current [state] — the unit exposed by
 * [Downloader.tasks].
 */
data class DownloadTask(
    val id: String,
    val request: DownloadRequest,
    val state: DownloadState,
)
