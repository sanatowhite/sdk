package io.sanato.appkit.download

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.sanato.appkit.core.net.HttpClientFactory
import io.sanato.appkit.core.net.RetryInterceptor
import io.sanato.appkit.download.engine.OkHttpDownloadEngine
import io.sanato.appkit.download.notify.AndroidDownloadNotifier
import io.sanato.appkit.download.notify.DownloadNotifier
import io.sanato.appkit.download.notify.DownloadService
import io.sanato.appkit.download.queue.DownloadQueue
import io.sanato.appkit.download.store.TaskStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import kotlin.time.Duration

/**
 * Public entry point. One process-wide instance per [Context] (see
 * [getInstance]) — same singleton-accessor shape as
 * [android.app.DownloadManager]/`androidx.work.WorkManager`, and necessary
 * for the same reason: `notify.DownloadService` is instantiated by the OS,
 * not by application code, so it has no constructor-injection path to reach
 * whichever [Downloader] the rest of the app is using — it has to look one
 * up by a well-known accessor instead.
 *
 * Construction takes a real [Context] and wires a real [OkHttpClient] +
 * on-disk [store.TaskStore] + `queue.DownloadQueue`; the actual transfer work
 * runs on this instance's own [CoroutineScope], independent of any
 * particular `Activity`/`Service` lifecycle — a task keeps making progress
 * whether or not anything is currently observing [tasks].
 */
class Downloader internal constructor(
    private val appContext: Context,
    private val queue: DownloadQueue,
    internal val notifier: DownloadNotifier,
    private val notificationsEnabled: Boolean,
) {
    /** Every known task, most recently changed first is *not* guaranteed — this is queue order, not history order. */
    val tasks: StateFlow<List<DownloadTask>> get() = queue.tasks

    /**
     * Enqueues [request]. Calling this again with an equivalent request
     * (same [DownloadRequest.url] + [DownloadRequest.fileName] — e.g. after a
     * process restart) is idempotent: it resumes the existing task rather
     * than starting a duplicate transfer. Returns the derived task id.
     */
    fun enqueue(request: DownloadRequest): String {
        val id = queue.enqueue(request)
        if (notificationsEnabled) DownloadService.ensureStarted(appContext)
        return id
    }

    fun pause(id: String) = queue.pause(id)

    fun resume(id: String) {
        queue.resume(id)
        if (notificationsEnabled) DownloadService.ensureStarted(appContext)
    }

    fun cancel(id: String) = queue.cancel(id)

    fun cancelAll() = queue.cancelAll()

    /** `null` once/if [id] is not (or no longer) known — e.g. it was never enqueued, or [cancel] already dropped it. */
    fun observe(id: String): Flow<DownloadState?> =
        tasks.map { list -> list.find { it.id == id }?.state }.distinctUntilChanged()

    /**
     * Always `true` below API 33 (the permission didn't exist yet). Callers
     * decide when/whether to request it — `:downloadkit` never prompts on
     * its own; a task without this permission still downloads normally, it
     * just has no visible notification (see [DownloadNotifier]'s contract).
     */
    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        @Volatile
        private var instance: Downloader? = null

        /**
         * Test-only escape hatch. [instance] otherwise persists for the whole
         * process (by design — see the class KDoc), which includes surviving
         * across test methods that share a JVM/classloader (Robolectric only
         * spins up a fresh one when `@Config` actually differs between
         * methods) — without this, a later test's [getInstance] call would
         * silently return an earlier test's instance, pointed at a
         * `DownloadConfig.downloadDir` that test's own cleanup already deleted.
         */
        internal fun resetForTesting() {
            instance = null
        }

        /**
         * [config]/[client]/[notifier] are only consulted the *first* time this
         * is called for the process — same "first caller wins" contract as
         * `WorkManager.getInstance`. A consumer that needs a specific
         * configuration should call this once, early (e.g. from
         * `Application.onCreate`), before any other call site can race it with
         * defaults.
         */
        fun getInstance(
            context: Context,
            config: DownloadConfig = DownloadConfig.default(context),
            client: OkHttpClient = downloadOkHttpClient(HttpClientFactory.okHttpClient()),
            notifier: DownloadNotifier? = null,
        ): Downloader =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext, config, client, notifier).also { instance = it }
            }

        private fun create(
            appContext: Context,
            config: DownloadConfig,
            client: OkHttpClient,
            notifier: DownloadNotifier?,
        ): Downloader {
            val store = TaskStore(config.downloadDir)
            val engine = OkHttpDownloadEngine(client)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue =
                DownloadQueue(
                    scope = scope,
                    config = config,
                    store = store,
                    engine = engine,
                    initialTasks = store.loadAll(),
                )
            return Downloader(
                appContext,
                queue,
                notifier ?: AndroidDownloadNotifier(appContext),
                config.notificationsEnabled,
            )
        }

        /**
         * `:core-net`'s [HttpClientFactory.okHttpClient] sets `callTimeout(30s)`
         * / `readTimeout(15s)` — both fatal to any transfer larger than a few
         * seconds. Same derivation pattern as `ws.WebSocketFactory.webSocketOkHttpClient`:
         * never hand `HttpClientFactory`'s client directly to [Downloader],
         * always derive through this first. [RetryInterceptor]'s blocking
         * `Thread.sleep` retries are also removed — `queue.DownloadQueue` has
         * its own attempt/backoff policy ([DownloadRetryPolicy]); stacking both
         * would multiply retries and hold an OkHttp dispatcher thread hostage
         * for the interceptor's own sleep on top of it.
         */
        fun downloadOkHttpClient(base: OkHttpClient): OkHttpClient {
            val builder =
                base
                    .newBuilder()
                    .callTimeout(Duration.ZERO)
                    .readTimeout(Duration.ZERO)
            builder.interceptors().removeAll { it is RetryInterceptor }
            return builder.build()
        }
    }
}
