package io.sanato.appkit.download.notify

import android.app.Notification

/**
 * One row worth of information for the summary notification —
 * `:downloadkit` deliberately shows one notification for the whole queue,
 * never one per task (see `DownloadService`'s KDoc for why).
 */
data class DownloadSummary(
    val activeCount: Int,
    val totalCount: Int,
    /** `-1` while at least one active task's total size is still unknown (no `Content-Length`/`Content-Range` seen yet). */
    val overallPercent: Int,
    val canPause: Boolean,
    val canResume: Boolean,
)

/**
 * The notification surface `DownloadService` drives. A consumer who wants
 * fully custom notification styling (icon, color, deep link) implements this
 * instead of using [AndroidDownloadNotifier] — `:downloadkit-hilt`'s
 * `DownloadBindsModule` exposes a `@BindsOptionalOf` hook for exactly this.
 *
 * Every method here must be safe to call when `POST_NOTIFICATIONS` hasn't
 * been granted (API 33+): implementations must not throw, they should simply
 * have no visible effect. [AndroidDownloadNotifier] follows this rule; a
 * custom implementation must too.
 */
interface DownloadNotifier {
    /**
     * The id [DownloadService] must pass to [android.app.Service.startForeground] —
     * every [Notification] this instance posts (from [buildForegroundNotification]
     * or [notifyProgress]) must use this same id, or the OS-mandated foreground
     * notification and this notifier's own progress notification show up as
     * two separate entries in the shade instead of one being updated in place.
     */
    val notificationId: Int

    /**
     * Built once, synchronously, right before [android.app.Service.startForeground]
     * — that call requires a [Notification] instance up front, before any
     * asynchronous work (loading a real progress number) has had a chance to run.
     */
    fun buildForegroundNotification(summary: DownloadSummary): Notification

    /** Called at most a few times a second — implementations should not do their own additional throttling. */
    fun notifyProgress(summary: DownloadSummary)

    /**
     * The `dataSync` foreground service type has a ~6 hour execution limit
     * (API 35+, see [android.app.Service.onTimeout]) — called when that limit
     * is hit and every in-flight transfer has just been paused. Distinct from
     * [notifyProgress] because it's a **non-foreground** notification: the
     * service is stopping right after this, so nothing here may depend on
     * the foreground notification still being alive.
     */
    fun notifyPausedByTimeout()

    /** Dismiss whatever [notifyProgress]/[notifyPausedByTimeout] last posted — called once the queue goes fully idle. */
    fun clear()
}
