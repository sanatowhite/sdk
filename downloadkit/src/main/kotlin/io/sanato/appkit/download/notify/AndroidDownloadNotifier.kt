package io.sanato.appkit.download.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.sanato.appkit.download.R

/**
 * Default [DownloadNotifier]. One channel (`appkit_download`, `IMPORTANCE_LOW`
 * so a progress tick doesn't ring/vibrate on every update), one notification
 * id, always a summary — never one notification per task (see
 * [DownloadService]'s KDoc).
 *
 * Every method here is safe to call without `POST_NOTIFICATIONS` granted:
 * [NotificationManagerCompat.notify] is itself a silent no-op when the
 * permission is missing on API 33+, so there is nothing extra to guard here
 * — this class simply never crashes on that path, which is the contract
 * [DownloadNotifier] requires of every implementation.
 */
class AndroidDownloadNotifier(
    private val context: Context,
) : DownloadNotifier {
    private val notificationManager = NotificationManagerCompat.from(context)

    override val notificationId: Int = NOTIFICATION_ID

    init {
        ensureChannel()
    }

    override fun buildForegroundNotification(summary: DownloadSummary): Notification =
        buildProgressNotification(summary)

    // POST_NOTIFICATIONS is a revocable runtime permission on API 33+, which is
    // exactly what NotificationManagerCompat.notify() exists to handle: it
    // checks internally and silently no-ops instead of throwing when the
    // permission is missing — see this class's own KDoc contract. Lint's
    // MissingPermission check can't see that internal check, only that the
    // permission "may be rejected."
    @SuppressLint("MissingPermission")
    override fun notifyProgress(summary: DownloadSummary) {
        notificationManager.notify(NOTIFICATION_ID, buildProgressNotification(summary))
    }

    @SuppressLint("MissingPermission")
    override fun notifyPausedByTimeout() {
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.download_notification_title_paused_timeout))
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun clear() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun buildProgressNotification(summary: DownloadSummary): Notification {
        val percentText = if (summary.overallPercent >= 0) "${summary.overallPercent}%" else "…"
        val title =
            context.getString(
                R.string.download_notification_title_progress,
                summary.activeCount,
                summary.totalCount,
                summary.overallPercent.coerceAtLeast(0),
            )
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(percentText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(PROGRESS_MAX, summary.overallPercent.coerceIn(0, PROGRESS_MAX), summary.overallPercent < 0)

        if (summary.canPause) {
            builder.addAction(
                0,
                context.getString(R.string.download_notification_action_pause),
                actionPendingIntent(DownloadService.ACTION_PAUSE_ALL, REQUEST_CODE_PAUSE),
            )
        } else if (summary.canResume) {
            builder.addAction(
                0,
                context.getString(R.string.download_notification_action_resume),
                actionPendingIntent(DownloadService.ACTION_RESUME_ALL, REQUEST_CODE_RESUME),
            )
        }
        builder.addAction(
            0,
            context.getString(R.string.download_notification_action_cancel_all),
            actionPendingIntent(DownloadService.ACTION_CANCEL_ALL, REQUEST_CODE_CANCEL),
        )
        return builder.build()
    }

    private fun actionPendingIntent(
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, DownloadService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        // NotificationChannel doesn't exist before API 26 — minSdk here is 24.
        // Below O, NotificationCompat.Builder's setPriority(PRIORITY_LOW) (used
        // throughout this class) is what controls the equivalent behavior instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "appkit_download"
        const val NOTIFICATION_ID = 0x646C6B69 // arbitrary stable id
        private const val PROGRESS_MAX = 100
        private const val REQUEST_CODE_PAUSE = 1
        private const val REQUEST_CODE_RESUME = 2
        private const val REQUEST_CODE_CANCEL = 3
    }
}
