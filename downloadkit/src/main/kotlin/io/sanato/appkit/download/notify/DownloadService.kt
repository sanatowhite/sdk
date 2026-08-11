package io.sanato.appkit.download.notify

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import io.sanato.appkit.download.DownloadState
import io.sanato.appkit.download.DownloadTask
import io.sanato.appkit.download.Downloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Foreground-service presence for `:downloadkit`. Shows exactly one
 * *summary* notification for the whole queue ("Downloading 2/5 · 43%"),
 * never one per task — a user downloading five files does not want five
 * persistent notifications.
 *
 * This service does **not** run the transfers itself — [Downloader] (backed
 * by `queue.DownloadQueue`) does that on its own app-scoped [CoroutineScope],
 * independent of this service's lifecycle. This service only (a) keeps the
 * process foreground-priority while at least one task is active, and
 * (b) mirrors [Downloader.tasks] into [DownloadNotifier]. This service being
 * killed or never started (see [ensureStarted]'s KDoc on the API 31+
 * background-start restriction) never loses transfer progress —
 * `store.TaskStore`'s on-disk checkpoints already cover that; it just means
 * the user stops seeing a progress bar until the app is foregrounded again.
 */
internal class DownloadService : Service() {
    private var scope: CoroutineScope? = null
    private lateinit var downloader: Downloader

    /** Test seam: set before [onCreate] runs (e.g. via Robolectric's `ServiceController.get()` before `.create()`) to bypass the real [Downloader.getInstance] singleton. */
    internal var downloaderOverride: Downloader? = null

    override fun onCreate() {
        super.onCreate()
        downloader = downloaderOverride ?: Downloader.getInstance(applicationContext)
        val jobScope = CoroutineScope(Dispatchers.Default)
        scope = jobScope
        downloader.tasks.onEach(::onTasksChanged).launchIn(jobScope)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Must call startForeground() promptly after being started via
        // startForegroundService() regardless of whether there's anything to
        // show yet — otherwise the OS kills the app with
        // ForegroundServiceDidNotStartInTimeException (API 31+). The onCreate
        // flow subscription above will supersede this with a real summary
        // (or stop the service outright) on its very next emission.
        startForegroundCompat(downloader.notifier.buildForegroundNotification(summarize(downloader.tasks.value)))

        when (intent?.action) {
            ACTION_PAUSE_ALL -> pauseAllRunning()
            ACTION_RESUME_ALL -> resumeAllPaused()
            ACTION_CANCEL_ALL -> downloader.cancelAll()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * API 35+: the `dataSync` foreground service type has a ~6 hour execution
     * limit ([Service.onTimeout]). We get a few seconds' warning before the
     * OS forcibly stops the service — pause everything and get out of
     * foreground cleanly rather than being killed mid-write.
     */
    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        pauseAllRunning()
        downloader.notifier.notifyPausedByTimeout()
        stopForegroundCompat()
        stopSelf(startId)
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private fun pauseAllRunning() {
        downloader.tasks.value
            .filter { it.state is DownloadState.Running }
            .forEach { downloader.pause(it.id) }
    }

    private fun resumeAllPaused() {
        downloader.tasks.value
            .filter { it.state is DownloadState.Paused }
            .forEach { downloader.resume(it.id) }
    }

    private fun onTasksChanged(taskList: List<DownloadTask>) {
        val active = taskList.filter { it.state is DownloadState.Running || it.state is DownloadState.Queued }
        if (active.isEmpty()) {
            downloader.notifier.clear()
            stopForegroundCompat()
            stopSelf()
            return
        }
        val summary = summarize(taskList)
        startForegroundCompat(downloader.notifier.buildForegroundNotification(summary))
        downloader.notifier.notifyProgress(summary)
    }

    // internal (not private) purely for direct unit testing — see DownloadServiceTest.
    internal fun summarize(taskList: List<DownloadTask>): DownloadSummary {
        val running = taskList.filter { it.state is DownloadState.Running }
        val active = taskList.filter { it.state is DownloadState.Running || it.state is DownloadState.Queued }
        val runningStates = running.map { it.state as DownloadState.Running }
        val allTotalsKnown = runningStates.isNotEmpty() && runningStates.all { it.totalBytes > 0 }
        val overallPercent =
            if (!allTotalsKnown) {
                -1
            } else {
                val downloaded = runningStates.sumOf { it.bytesDownloaded }
                val total = runningStates.sumOf { it.totalBytes }
                if (total > 0) ((downloaded * 100) / total).toInt() else -1
            }
        return DownloadSummary(
            activeCount = active.size,
            totalCount = taskList.size,
            overallPercent = overallPercent,
            canPause = running.isNotEmpty(),
            canResume = taskList.any { it.state is DownloadState.Paused },
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        val id = downloader.notifier.notificationId
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(id, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        const val ACTION_PAUSE_ALL = "io.sanato.appkit.download.action.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "io.sanato.appkit.download.action.RESUME_ALL"
        const val ACTION_CANCEL_ALL = "io.sanato.appkit.download.action.CANCEL_ALL"

        /**
         * Best-effort: on API 31+, calling this while the app is backgrounded
         * throws [IllegalStateException] (specifically
         * `ForegroundServiceStartNotAllowedException`, which extends it) — caught
         * and swallowed here deliberately. The download queue keeps making
         * progress via its own [CoroutineScope] regardless; the task just won't
         * have a visible notification until the app is foregrounded again and
         * this is retried (e.g. from [Downloader.enqueue]/`resume`).
         */
        fun ensureStarted(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // See KDoc above — intentionally swallowed.
            }
        }
    }
}
