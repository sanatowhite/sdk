package io.sanato.appkit.download.notify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.sanato.appkit.download.DownloadConfig
import io.sanato.appkit.download.DownloadRequest
import io.sanato.appkit.download.DownloadState
import io.sanato.appkit.download.DownloadTask
import io.sanato.appkit.download.Downloader
import io.sanato.appkit.download.engine.DownloadEngine
import io.sanato.appkit.download.engine.ResponseInfo
import io.sanato.appkit.download.engine.ResumeInfo
import io.sanato.appkit.download.queue.DownloadQueue
import io.sanato.appkit.download.store.TaskStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Covers the three platform behaviors called out in the design as needing
 * explicit coverage: API 35+ FGS timeout, the API 31+ background-start
 * rejection, and the pure summary math that drives the notification text.
 * Not covered here (needs a physical/emulator device, see the module
 * README's manual test plan): actual `POST_NOTIFICATIONS` prompt behavior —
 * [AndroidDownloadNotifier] is written so a missing permission is a silent
 * no-op by construction ([androidx.core.app.NotificationManagerCompat.notify]
 * itself never throws for it), which is what's asserted here instead.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadServiceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeNotifier : DownloadNotifier {
        override val notificationId: Int = 999
        var pausedByTimeoutCalled = false
        var cleared = false
        val progressCalls = mutableListOf<DownloadSummary>()

        override fun buildForegroundNotification(summary: DownloadSummary): Notification =
            Notification.Builder(ApplicationProvider.getApplicationContext(), CHANNEL_ID_UNUSED).build()

        override fun notifyProgress(summary: DownloadSummary) {
            progressCalls += summary
        }

        override fun notifyPausedByTimeout() {
            pausedByTimeoutCalled = true
        }

        override fun clear() {
            cleared = true
        }

        private companion object {
            const val CHANNEL_ID_UNUSED = "test"
        }
    }

    /** Emits headers + partial progress, then blocks on [gate] until the test releases it. */
    private class GateEngine(
        private val gate: CompletableDeferred<Unit>,
    ) : DownloadEngine {
        override suspend fun download(
            url: String,
            headers: Map<String, String>,
            destination: File,
            resume: ResumeInfo,
            onHeaders: (ResponseInfo) -> Unit,
            onProgress: (Long) -> Unit,
        ) {
            onHeaders(ResponseInfo(100, null, null, true))
            onProgress(10)
            gate.await()
        }
    }

    private fun newDownloader(
        engine: DownloadEngine,
        notifier: DownloadNotifier,
    ): Downloader {
        val downloadDir = tempFolder.newFolder()
        val store = TaskStore(downloadDir)
        val config = DownloadConfig(downloadDir = downloadDir, maxConcurrent = 2)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = DownloadQueue(scope = scope, config = config, store = store, engine = engine)
        return Downloader(ApplicationProvider.getApplicationContext(), queue, notifier, true)
    }

    /** Real coroutines on a real dispatcher — this exercises the service/`Downloader` singleton wiring, not `DownloadQueue`'s internals (see `queue.DownloadQueueTest` for virtual-time coverage of that), so we poll instead of controlling virtual time. */
    private fun awaitState(
        downloader: Downloader,
        predicate: (DownloadState) -> Boolean,
    ) = runBlocking {
        withTimeout(5_000) {
            while (downloader.tasks.value.none { predicate(it.state) }) {
                delay(10)
            }
        }
    }

    @Test
    fun `onTimeout pauses every running task and notifies via notifyPausedByTimeout`() {
        val gate = CompletableDeferred<Unit>()
        val notifier = FakeNotifier()
        val downloader = newDownloader(GateEngine(gate), notifier)
        downloader.enqueue(DownloadRequest(url = "https://example.invalid/f", fileName = "f.bin"))
        awaitState(downloader) { it is DownloadState.Running }

        val controller = Robolectric.buildService(DownloadService::class.java)
        val service = controller.get()
        service.downloaderOverride = downloader
        controller.create()

        service.onTimeout(1, 0)

        awaitState(downloader) { it is DownloadState.Paused }
        assertTrue(notifier.pausedByTimeoutCalled)
    }

    @Test
    fun `ACTION_CANCEL_ALL cancels every tracked task`() {
        val gate = CompletableDeferred<Unit>()
        val notifier = FakeNotifier()
        val downloader = newDownloader(GateEngine(gate), notifier)
        downloader.enqueue(DownloadRequest(url = "https://example.invalid/f", fileName = "f.bin"))
        awaitState(downloader) { it is DownloadState.Running }

        val controller = Robolectric.buildService(DownloadService::class.java)
        val service = controller.get()
        service.downloaderOverride = downloader
        controller.create()

        service.onStartCommand(Intent(DownloadService.ACTION_CANCEL_ALL), 0, 1)

        awaitState(downloader) { it is DownloadState.Canceled }
    }

    @Test
    fun `ensureStarted swallows the API 31+ background-start rejection`() {
        val realContext = ApplicationProvider.getApplicationContext<Context>()
        val restrictedContext =
            object : ContextWrapper(realContext) {
                override fun startForegroundService(service: Intent): ComponentName? =
                    throw IllegalStateException("app is in the background")
            }

        // Must not throw — the queue keeps running without a visible notification.
        DownloadService.ensureStarted(restrictedContext)
    }

    @Test
    fun `summarize reports unknown percent until every running task has a known total`() {
        val service = DownloadService()
        val tasks =
            listOf(
                task("a", DownloadState.Running(bytesDownloaded = 10, totalBytes = -1)),
                task("b", DownloadState.Running(bytesDownloaded = 20, totalBytes = 100)),
            )

        val summary = service.summarize(tasks)

        assertEquals(-1, summary.overallPercent)
        assertEquals(2, summary.activeCount)
        assertTrue(summary.canPause)
        assertFalse(summary.canResume)
    }

    @Test
    fun `summarize computes an aggregate percent once every running task's total is known`() {
        val service = DownloadService()
        val tasks =
            listOf(
                task("a", DownloadState.Running(bytesDownloaded = 25, totalBytes = 100)),
                task("b", DownloadState.Running(bytesDownloaded = 25, totalBytes = 100)),
            )

        val summary = service.summarize(tasks)

        assertEquals(25, summary.overallPercent) // (25+25)/(100+100) = 25%
    }

    @Test
    fun `summarize offers resume when any task is Paused and no task is Running`() {
        val service = DownloadService()
        val tasks = listOf(task("a", DownloadState.Paused(bytesDownloaded = 10, totalBytes = 100)))

        val summary = service.summarize(tasks)

        assertFalse(summary.canPause)
        assertTrue(summary.canResume)
        assertEquals(0, summary.activeCount) // Paused isn't "active" — see DownloadService.onTasksChanged
    }

    private fun task(
        name: String,
        state: DownloadState,
    ) = DownloadTask(
        id = name,
        request = DownloadRequest(url = "https://example.invalid/$name", fileName = "$name.bin"),
        state = state,
    )
}
