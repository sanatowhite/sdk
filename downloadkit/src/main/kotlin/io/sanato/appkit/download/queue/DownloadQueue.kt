package io.sanato.appkit.download.queue

import io.sanato.appkit.download.DownloadConfig
import io.sanato.appkit.download.DownloadError
import io.sanato.appkit.download.DownloadRequest
import io.sanato.appkit.download.DownloadState
import io.sanato.appkit.download.DownloadTask
import io.sanato.appkit.download.Sha256
import io.sanato.appkit.download.engine.DownloadEngine
import io.sanato.appkit.download.engine.ResponseInfo
import io.sanato.appkit.download.engine.ResumeInfo
import io.sanato.appkit.download.store.PersistedState
import io.sanato.appkit.download.store.TaskMetadata
import io.sanato.appkit.download.store.TaskStore
import io.sanato.appkit.download.taskIdFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Owns every in-memory [DownloadTask] and drives the actual transfers.
 *
 * Concurrency model: same discipline as `:core-net`'s `ws.RealWebSocketConnection`
 * — a single [events] channel serializes every input (public commands *and*
 * worker progress/completion callbacks) into one coroutine ([runLoop]) that
 * owns [tasksById] and [activeWorkers]. Nothing here needs a `Mutex` or a
 * `Semaphore`: since only the loop coroutine ever decides "start a new
 * worker," comparing `activeWorkers.size` to [DownloadConfig.maxConcurrent]
 * enforces the concurrency cap just as reliably as a `Semaphore` would,
 * without introducing a second synchronization primitive alongside the
 * channel.
 *
 * Each running task gets its own worker coroutine ([runWorker]) that owns
 * *its own* local retry state (attempt count, last-seen validators) and talks
 * to the loop only by sending [Event]s — it never reads or writes [tasksById]
 * directly. [Entry.generation] guards against a stale event from a worker the
 * loop has since superseded (paused/canceled, then resumed) being
 * misapplied — the same technique `RealWebSocketConnection` uses for its
 * socket-lifecycle events.
 */
internal class DownloadQueue(
    private val scope: CoroutineScope,
    private val config: DownloadConfig,
    private val store: TaskStore,
    private val engine: DownloadEngine,
    initialTasks: List<TaskMetadata> = emptyList(),
    private val random: Random = Random.Default,
    // Never hardcode Dispatchers.IO inline — same reasoning as :core-common's
    // DispatcherQualifiers (IoDispatcher/DefaultDispatcher/MainImmediateDispatcher):
    // a caller under `runTest`'s virtual scheduler needs to substitute its own
    // TestDispatcher here, or blocking file I/O silently escapes onto a real
    // thread pool that `runCurrent()`/`advanceTimeBy()` have no visibility into.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val events = Channel<Event>(Channel.UNLIMITED)
    private val tasksById = LinkedHashMap<String, Entry>()
    private val activeWorkers = HashMap<String, Job>()

    init {
        initialTasks.forEach { meta -> tasksById[meta.id] = Entry.fromMetadata(meta) }
        publish()
        scope.launch { runLoop() }
        events.trySend(Event.Schedule)
    }

    fun enqueue(request: DownloadRequest): String {
        val id = taskIdFor(request.url, request.fileName)
        events.trySend(Event.Enqueue(request))
        return id
    }

    fun pause(id: String) {
        events.trySend(Event.Pause(id))
    }

    fun resume(id: String) {
        events.trySend(Event.Resume(id))
    }

    fun cancel(id: String) {
        events.trySend(Event.Cancel(id))
    }

    fun cancelAll() {
        events.trySend(Event.CancelAll)
    }

    // ── Internal model ──────────────────────────────────────────────

    private sealed interface Phase {
        data object QUEUED : Phase

        data object RUNNING : Phase

        data object PAUSED : Phase

        data object FAILED : Phase

        data object CANCELED : Phase

        data class COMPLETED(
            val file: File,
        ) : Phase
    }

    private data class Entry(
        val request: DownloadRequest,
        val phase: Phase,
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = -1L,
        val etag: String? = null,
        val lastModified: String? = null,
        val acceptRanges: Boolean = true,
        val lastError: DownloadError? = null,
        /** Bumped every time a worker is (re)launched or torn down for this id. */
        val generation: Int = 0,
    ) {
        fun toPublicState(): DownloadState =
            when (val p = phase) {
                Phase.QUEUED -> DownloadState.Queued
                Phase.RUNNING -> DownloadState.Running(bytesDownloaded, totalBytes)
                Phase.PAUSED -> DownloadState.Paused(bytesDownloaded, totalBytes)
                Phase.FAILED -> DownloadState.Failed(requireNotNull(lastError), bytesDownloaded, totalBytes)
                Phase.CANCELED -> DownloadState.Canceled
                is Phase.COMPLETED -> DownloadState.Completed(p.file, totalBytes)
            }

        companion object {
            fun fromMetadata(meta: TaskMetadata): Entry =
                Entry(
                    request =
                        DownloadRequest(
                            url = meta.url,
                            fileName = meta.fileName,
                            destDir = File(meta.destDir),
                            sha256 = meta.sha256,
                            headers = meta.headers,
                            allowMetered = meta.allowMetered,
                            priority = meta.priority,
                        ),
                    phase = if (meta.state == PersistedState.QUEUED) Phase.QUEUED else Phase.PAUSED,
                    bytesDownloaded = meta.bytesDownloaded,
                    totalBytes = meta.totalBytes,
                    etag = meta.etag,
                    lastModified = meta.lastModified,
                    acceptRanges = meta.acceptRanges,
                )
        }
    }

    private sealed interface Event {
        data class Enqueue(
            val request: DownloadRequest,
        ) : Event

        data class Pause(
            val id: String,
        ) : Event

        data class Resume(
            val id: String,
        ) : Event

        data class Cancel(
            val id: String,
        ) : Event

        data object CancelAll : Event

        data object Schedule : Event

        data class Headers(
            val id: String,
            val generation: Int,
            val info: ResponseInfo,
        ) : Event

        data class Progress(
            val id: String,
            val generation: Int,
            val bytesDownloaded: Long,
            val totalBytes: Long,
        ) : Event

        data class Succeeded(
            val id: String,
            val generation: Int,
            val file: File,
            val totalBytes: Long,
        ) : Event

        data class AttemptFailed(
            val id: String,
            val generation: Int,
            val error: DownloadError,
            val bytesDownloaded: Long,
        ) : Event

        data class GaveUp(
            val id: String,
            val generation: Int,
            val error: DownloadError,
            val bytesDownloaded: Long,
            val totalBytes: Long,
        ) : Event
    }

    // ── The loop: the only code allowed to mutate tasksById/activeWorkers ──

    private suspend fun runLoop() {
        for (event in events) {
            when (event) {
                is Event.Enqueue -> handleEnqueue(event.request)
                is Event.Pause -> handlePause(event.id)
                is Event.Resume -> handleResume(event.id)
                is Event.Cancel -> handleCancel(event.id)
                Event.CancelAll -> tasksById.keys.toList().forEach(::handleCancel)
                Event.Schedule -> scheduleNext()
                is Event.Headers -> handleHeaders(event)
                is Event.Progress -> handleProgress(event)
                is Event.Succeeded -> handleSucceeded(event)
                is Event.AttemptFailed -> handleAttemptFailed(event)
                is Event.GaveUp -> handleGaveUp(event)
            }
        }
    }

    private fun handleEnqueue(request: DownloadRequest) {
        val id = taskIdFor(request.url, request.fileName)
        val existing = tasksById[id]
        // Idempotent: a task already Queued/Running/Paused/Failed keeps its
        // progress untouched — this is what lets a caller re-`enqueue()` the
        // same request after a process restart and transparently resume
        // rather than starting a duplicate transfer.
        val alreadyTracked = existing != null && existing.phase != Phase.CANCELED && existing.phase !is Phase.COMPLETED
        if (alreadyTracked) return

        tasksById[id] = Entry(request = request, phase = Phase.QUEUED)
        store.save(metaFor(id, request, bytesDownloaded = 0L, totalBytes = -1L, state = PersistedState.QUEUED))
        publish()
        scheduleNext()
    }

    private fun handlePause(id: String) {
        val entry = tasksById[id] ?: return
        if (entry.phase != Phase.RUNNING && entry.phase != Phase.QUEUED) return
        activeWorkers.remove(id)?.cancel()
        tasksById[id] = entry.copy(phase = Phase.PAUSED, generation = entry.generation + 1)
        publish()
        scheduleNext()
    }

    private fun handleResume(id: String) {
        val entry = tasksById[id] ?: return
        if (entry.phase != Phase.PAUSED && entry.phase != Phase.FAILED) return
        tasksById[id] = entry.copy(phase = Phase.QUEUED)
        publish()
        scheduleNext()
    }

    private fun handleCancel(id: String) {
        val entry = tasksById[id] ?: return
        if (entry.phase == Phase.CANCELED || entry.phase is Phase.COMPLETED) return
        activeWorkers.remove(id)?.cancel()
        store.delete(id)
        tasksById[id] = entry.copy(phase = Phase.CANCELED, generation = entry.generation + 1)
        publish()
        scheduleNext()
    }

    private fun scheduleNext() {
        while (activeWorkers.size < config.maxConcurrent) {
            val next =
                tasksById.entries
                    .filter { it.value.phase == Phase.QUEUED }
                    .maxByOrNull { it.value.request.priority }
                    ?: return
            startWorker(next.key, next.value)
        }
    }

    private fun startWorker(
        id: String,
        entry: Entry,
    ) {
        val generation = entry.generation + 1
        tasksById[id] = entry.copy(phase = Phase.RUNNING, generation = generation)
        publish()

        val destination = store.partFile(id)
        val resume = ResumeInfo(entry.bytesDownloaded, entry.etag, entry.lastModified, entry.acceptRanges)
        activeWorkers[id] = scope.launch { runWorker(id, generation, entry.request, destination, resume) }
    }

    private fun handleHeaders(event: Event.Headers) {
        val entry = tasksById[event.id] ?: return
        if (entry.generation != event.generation) return
        tasksById[event.id] =
            entry.copy(
                totalBytes = event.info.totalBytes,
                etag = event.info.etag,
                lastModified = event.info.lastModified,
                acceptRanges = event.info.acceptRanges,
            )
        publish()
    }

    private fun handleProgress(event: Event.Progress) {
        val entry = tasksById[event.id] ?: return
        if (entry.generation != event.generation) return
        tasksById[event.id] =
            entry.copy(
                bytesDownloaded = event.bytesDownloaded,
                totalBytes = if (event.totalBytes > 0) event.totalBytes else entry.totalBytes,
            )
        publish()
    }

    private fun handleSucceeded(event: Event.Succeeded) {
        val entry = tasksById[event.id] ?: return
        // superseded — the current worker owns this id's lifecycle now.
        if (entry.generation != event.generation) return
        activeWorkers.remove(event.id)
        tasksById[event.id] =
            entry.copy(
                phase = Phase.COMPLETED(event.file),
                bytesDownloaded = event.totalBytes,
                totalBytes = event.totalBytes,
            )
        publish()
        scheduleNext()
    }

    private fun handleAttemptFailed(event: Event.AttemptFailed) {
        // Retryable — the worker is already sleeping through its own backoff
        // and will retry itself. This only updates the observable numbers; it
        // does not free the concurrency slot or touch activeWorkers.
        val entry = tasksById[event.id] ?: return
        if (entry.generation != event.generation) return
        tasksById[event.id] = entry.copy(bytesDownloaded = event.bytesDownloaded, lastError = event.error)
        publish()
    }

    private fun handleGaveUp(event: Event.GaveUp) {
        val entry = tasksById[event.id] ?: return
        if (entry.generation != event.generation) return
        activeWorkers.remove(event.id)
        tasksById[event.id] =
            entry.copy(
                phase = Phase.FAILED,
                bytesDownloaded = event.bytesDownloaded,
                totalBytes = event.totalBytes,
                lastError = event.error,
            )
        publish()
        scheduleNext()
    }

    private fun publish() {
        _tasks.value = tasksById.map { (id, entry) -> DownloadTask(id, entry.request, entry.toPublicState()) }
    }

    private fun metaFor(
        id: String,
        request: DownloadRequest,
        bytesDownloaded: Long,
        totalBytes: Long,
        etag: String? = null,
        lastModified: String? = null,
        acceptRanges: Boolean = true,
        state: PersistedState,
    ): TaskMetadata =
        TaskMetadata(
            id = id,
            url = request.url,
            fileName = request.fileName,
            destDir = (request.destDir ?: config.downloadDir).absolutePath,
            sha256 = request.sha256,
            headers = request.headers,
            allowMetered = request.allowMetered,
            priority = request.priority,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            etag = etag,
            lastModified = lastModified,
            acceptRanges = acceptRanges,
            state = state,
        )

    // ── The worker: one per actively-running task, owns only local state ──

    private suspend fun runWorker(
        id: String,
        generation: Int,
        request: DownloadRequest,
        destination: File,
        initialResume: ResumeInfo,
    ) {
        var resume = initialResume
        var attempt = 0
        var lastProgressSent = resume.bytesDownloaded
        var lastCheckpoint = resume.bytesDownloaded
        var latestInfo: ResponseInfo? = null
        // The very first progress callback of the whole worker always gets
        // through regardless of the byte-interval throttle below — otherwise
        // a slow connection (or a small file, well under the throttle
        // interval) would show zero progress movement until either the
        // threshold or completion. Byte-interval throttling only kicks in
        // once the caller has *something* to look at.
        var firstProgressReported = false

        // `onHeaders`/`onProgress` below are plain (non-suspend) callbacks, but
        // they run synchronously on whatever thread `engine.download` is
        // currently executing on — which is always a `Dispatchers.IO` thread
        // (see `OkHttpDownloadEngine`'s own `withContext`). Blocking file I/O
        // (`checkpoint`, via `store.save`) is therefore safe to call directly
        // from inside them, no additional dispatch needed.
        while (true) {
            try {
                engine.download(
                    url = request.url,
                    headers = request.headers,
                    destination = destination,
                    resume = resume,
                    onHeaders = { info ->
                        latestInfo = info
                        events.trySend(Event.Headers(id, generation, info))
                        checkpoint(id, request, resume.bytesDownloaded, info)
                    },
                    onProgress = { bytesDownloaded ->
                        if (!firstProgressReported ||
                            bytesDownloaded - lastProgressSent >= PROGRESS_EVENT_INTERVAL_BYTES ||
                            bytesDownloaded == latestInfo?.totalBytes
                        ) {
                            firstProgressReported = true
                            lastProgressSent = bytesDownloaded
                            events.trySend(
                                Event.Progress(id, generation, bytesDownloaded, latestInfo?.totalBytes ?: -1L),
                            )
                        }
                        if (bytesDownloaded - lastCheckpoint >= CHECKPOINT_INTERVAL_BYTES) {
                            lastCheckpoint = bytesDownloaded
                            checkpoint(id, request, bytesDownloaded, latestInfo)
                        }
                    },
                )
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadError) {
                attempt++
                val bytesSoFar = withContext(ioDispatcher) { destination.length() }
                if (attempt >= config.retryPolicy.maxAttempts) {
                    events.trySend(Event.GaveUp(id, generation, e, bytesSoFar, latestInfo?.totalBytes ?: -1L))
                    return
                }
                events.trySend(Event.AttemptFailed(id, generation, e, bytesSoFar))
                resume =
                    ResumeInfo(
                        bytesDownloaded = bytesSoFar,
                        etag = latestInfo?.etag ?: resume.etag,
                        lastModified = latestInfo?.lastModified ?: resume.lastModified,
                        acceptRanges = latestInfo?.acceptRanges ?: resume.acceptRanges,
                    )
                delay(backoffDelay(attempt))
            }
        }

        withContext(ioDispatcher) {
            // A leaked `activeWorkers` entry would permanently block this id's
            // concurrency slot — see handleSucceeded/handleGaveUp, both of which
            // are the *only* places that remove it. Any unexpected failure past
            // this point (disk full during the final copy, a `.part` file that
            // vanished from under us, ...) must still reach one of those two
            // handlers, not propagate uncaught out of this coroutine.
            try {
                val checksumError =
                    request.sha256?.let { expected ->
                        val actual = Sha256.hex(destination)
                        if (!actual.equals(
                                expected,
                                ignoreCase = true,
                            )
                        ) {
                            DownloadError.ChecksumMismatch(expected, actual)
                        } else {
                            null
                        }
                    }
                if (checksumError != null) {
                    events.trySend(
                        Event.GaveUp(
                            id,
                            generation,
                            checksumError,
                            destination.length(),
                            latestInfo?.totalBytes ?: -1L,
                        ),
                    )
                    return@withContext
                }
                val finalFile = store.destinationFile(request.destDir, request.fileName)
                destination.copyTo(finalFile, overwrite = true)
                destination.delete()
                store.delete(id)
                events.trySend(Event.Succeeded(id, generation, finalFile, latestInfo?.totalBytes ?: finalFile.length()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val wrapped = e as? DownloadError ?: DownloadError.Io(e)
                events.trySend(
                    Event.GaveUp(id, generation, wrapped, destination.length(), latestInfo?.totalBytes ?: -1L),
                )
            }
        }
    }

    private fun checkpoint(
        id: String,
        request: DownloadRequest,
        bytesDownloaded: Long,
        info: ResponseInfo?,
    ) {
        store.save(
            metaFor(
                id = id,
                request = request,
                bytesDownloaded = bytesDownloaded,
                totalBytes = info?.totalBytes ?: -1L,
                etag = info?.etag,
                lastModified = info?.lastModified,
                acceptRanges = info?.acceptRanges ?: true,
                state = PersistedState.PAUSED,
            ),
        )
    }

    private fun backoffDelay(attempt: Int): Duration {
        val policy = config.retryPolicy
        val base =
            (policy.initialDelay.inWholeMilliseconds * policy.multiplier.pow(attempt - 1))
                .toLong()
                .coerceAtMost(policy.maxDelay.inWholeMilliseconds)
        val jitter = (base * policy.jitterRatio * (random.nextDouble() * 2 - 1)).toLong()
        return (base + jitter).coerceAtLeast(0L).milliseconds
    }

    private companion object {
        const val PROGRESS_EVENT_INTERVAL_BYTES = 64L * 1024
        const val CHECKPOINT_INTERVAL_BYTES = 512L * 1024
    }
}
