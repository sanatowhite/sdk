package io.sanato.appkit.download.store

import kotlinx.serialization.Serializable

/**
 * The only two states worth persisting to disk. Everything else in
 * [io.sanato.appkit.download.DownloadState] is transient or has no on-disk
 * representation:
 * - `Running` is *never* written as such. While
 *   [io.sanato.appkit.download.queue.DownloadQueue] is actively transferring
 *   bytes, every progress checkpoint is flushed to disk as `PAUSED` with the
 *   current [TaskMetadata.bytesDownloaded] — that's the truthful at-rest
 *   description regardless of whether it's animated in memory right now, so
 *   a process death mid-transfer needs no special-case "downgrade" on load:
 *   [TaskStore.loadAll] just returns exactly what's on disk.
 * - `Completed`/`Canceled` delete the `.meta`/`.part` files outright — there
 *   is nothing left to resume.
 * - `Failed` (retries exhausted) is likewise persisted as `PAUSED` — the
 *   `.part` bytes are still good, a caller can retry later via
 *   [io.sanato.appkit.download.Downloader.resume].
 */
@Serializable
internal enum class PersistedState { QUEUED, PAUSED }

/**
 * Sidecar file (`<id>.meta`, JSON) next to the `<id>.part` bytes. [etag] /
 * [lastModified] / [acceptRanges] are what make range-resume *correct*
 * rather than merely possible — see `engine/OkHttpDownloadEngine.kt`'s KDoc
 * for why a bare `Range` header without `If-Range` can silently splice
 * mismatched byte ranges into one file.
 */
@Serializable
internal data class TaskMetadata(
    val id: String,
    val url: String,
    val fileName: String,
    val destDir: String,
    val sha256: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val allowMetered: Boolean = true,
    val priority: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val etag: String? = null,
    val lastModified: String? = null,
    /** `false` once the server has told us (via a `200` reply to a `Range` request) that it doesn't support resuming. */
    val acceptRanges: Boolean = true,
    val state: PersistedState = PersistedState.QUEUED,
)
