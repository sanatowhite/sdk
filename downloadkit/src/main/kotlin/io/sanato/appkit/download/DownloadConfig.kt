package io.sanato.appkit.download

import android.content.Context
import java.io.File

/**
 * [downloadDir] is a plain [File], not derived from a [Context] at construction
 * time — keeps this type constructible in a pure-JVM unit test without
 * Robolectric. Use [DownloadConfig.default] to get the same default directory
 * `:updatechecker`'s `ApkDownloader` uses (`getExternalFilesDir` with an
 * internal-storage fallback for devices without external storage mounted).
 */
data class DownloadConfig(
    val downloadDir: File,
    val maxConcurrent: Int = 3,
    val retryPolicy: DownloadRetryPolicy = DownloadRetryPolicy(),
    /**
     * Master switch for the foreground-service + notification path. `false`
     * means [Downloader] only ever runs the in-process queue (see
     * `queue/DownloadQueue.kt`) — no [android.app.Service], no
     * [android.app.Notification]. A consumer that wants silent background
     * transfers without a persistent notification sets this to `false` and
     * accepts that the OS may kill the process once it's backgrounded.
     */
    val notificationsEnabled: Boolean = true,
) {
    init {
        require(maxConcurrent >= 1) { "maxConcurrent must be >= 1, was $maxConcurrent" }
    }

    companion object {
        private const val DOWNLOAD_SUBDIR = "downloadkit"

        fun default(context: Context): DownloadConfig =
            DownloadConfig(
                downloadDir = File(context.getExternalFilesDir(null) ?: context.filesDir, DOWNLOAD_SUBDIR),
            )
    }
}
