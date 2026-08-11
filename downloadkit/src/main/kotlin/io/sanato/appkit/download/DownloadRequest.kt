package io.sanato.appkit.download

import java.io.File

/**
 * What to download. [id] is *not* part of this type — [Downloader.enqueue]
 * derives a stable id from [url] + [fileName] (see `taskIdFor` in
 * `store/TaskStore.kt`) so that calling `enqueue` again with an
 * equivalent request — e.g. after a process restart — resumes the same
 * on-disk task instead of starting a duplicate download.
 */
data class DownloadRequest(
    val url: String,
    val fileName: String,
    /** `null` means "use [DownloadConfig.downloadDir]". */
    val destDir: File? = null,
    /** Optional integrity check, verified after the transfer completes. */
    val sha256: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val allowMetered: Boolean = true,
    /** Higher runs first; ties broken by enqueue order. */
    val priority: Int = 0,
)
