package io.sanato.appkit.download.queue

import io.sanato.appkit.download.DownloadConfig
import io.sanato.appkit.download.DownloadError
import io.sanato.appkit.download.DownloadRequest
import io.sanato.appkit.download.DownloadRetryPolicy
import io.sanato.appkit.download.DownloadState
import io.sanato.appkit.download.engine.DownloadEngine
import io.sanato.appkit.download.engine.ResponseInfo
import io.sanato.appkit.download.engine.ResumeInfo
import io.sanato.appkit.download.store.TaskStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/**
 * Layer 1 for `:downloadkit`'s concurrency model — same shape as
 * `core-net/.../ws/RealWebSocketConnectionTest.kt`: drive the queue entirely
 * through a fake [DownloadEngine] and `runTest`'s virtual time, zero real I/O
 * or real sleeps, one assertion per bullet point of [DownloadQueue]'s KDoc.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun request(
        name: String,
        priority: Int = 0,
        sha256: String? = null,
    ) = DownloadRequest(
        url = "https://example.invalid/$name",
        fileName = "$name.bin",
        priority = priority,
        sha256 = sha256,
    )

    private fun defaultRetryPolicy() =
        DownloadRetryPolicy(
            maxAttempts = 3,
            initialDelay = 10.milliseconds,
            maxDelay = 50.milliseconds,
            jitterRatio = 0.0,
        )

    private fun TestScope.newQueue(
        engine: DownloadEngine,
        maxConcurrent: Int = 2,
        retryPolicy: DownloadRetryPolicy = defaultRetryPolicy(),
    ): DownloadQueue {
        val downloadDir = tempFolder.newFolder()
        val store = TaskStore(downloadDir)
        val config = DownloadConfig(downloadDir = downloadDir, maxConcurrent = maxConcurrent, retryPolicy = retryPolicy)
        // Blocking file I/O (checkpoint saves, checksum verify, final copy) must land on
        // *this* TestScope's own virtual scheduler — a real Dispatchers.IO would escape
        // to a real thread pool that runCurrent()/advanceTimeBy() can't see or wait for.
        return DownloadQueue(
            scope = backgroundScope,
            config = config,
            store = store,
            engine = engine,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
    }

    private fun DownloadQueue.stateOf(name: String): DownloadState? =
        tasks.value.find { it.request.fileName == "$name.bin" }?.state

    /** An engine whose per-call behavior is fully scripted by the test. */
    private class ScriptedEngine(
        private val behavior: suspend (
            url: String,
            resume: ResumeInfo,
            destination: File,
            onHeaders: (ResponseInfo) -> Unit,
            onProgress: (Long) -> Unit,
        ) -> Unit,
    ) : DownloadEngine {
        val callCount = AtomicInteger(0)

        override suspend fun download(
            url: String,
            headers: Map<String, String>,
            destination: File,
            resume: ResumeInfo,
            onHeaders: (ResponseInfo) -> Unit,
            onProgress: (Long) -> Unit,
        ) {
            callCount.incrementAndGet()
            behavior(url, resume, destination, onHeaders, onProgress)
        }
    }

    @Test
    fun `enqueue starts up to maxConcurrent tasks and leaves the rest Queued`() =
        runTest {
            val gates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
            val engine =
                ScriptedEngine { url, _, _, onHeaders, _ ->
                    onHeaders(ResponseInfo(100, null, null, true))
                    gates.getOrPut(url) { CompletableDeferred() }.await()
                }
            val queue = newQueue(engine, maxConcurrent = 2)

            queue.enqueue(request("a"))
            queue.enqueue(request("b"))
            queue.enqueue(request("c"))
            runCurrent()

            assertTrue(queue.stateOf("a") is DownloadState.Running)
            assertTrue(queue.stateOf("b") is DownloadState.Running)
            assertEquals(DownloadState.Queued, queue.stateOf("c"))
        }

    @Test
    fun `completing a running task frees a slot for the next queued task`() =
        runTest {
            val gates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
            val engine =
                ScriptedEngine { url, _, destination, onHeaders, onProgress ->
                    onHeaders(ResponseInfo(5, null, null, true))
                    gates.getOrPut(url) { CompletableDeferred() }.await()
                    destination.writeBytes("hello".toByteArray())
                    onProgress(5)
                }
            val queue = newQueue(engine, maxConcurrent = 1)

            queue.enqueue(request("a"))
            queue.enqueue(request("b"))
            runCurrent()
            assertTrue(queue.stateOf("a") is DownloadState.Running)
            assertEquals(DownloadState.Queued, queue.stateOf("b"))

            gates.getValue("https://example.invalid/a").complete(Unit)
            runCurrent()

            assertTrue(queue.stateOf("a") is DownloadState.Completed)
            assertTrue(queue.stateOf("b") is DownloadState.Running)
        }

    @Test
    fun `higher priority queued task is scheduled before a lower priority one`() =
        runTest {
            val gates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
            val engine =
                ScriptedEngine { url, _, destination, onHeaders, _ ->
                    onHeaders(ResponseInfo(10, null, null, true))
                    gates.getOrPut(url) { CompletableDeferred() }.await()
                    // A real engine always leaves *some* bytes on disk by the time it
                    // returns successfully — write them here so "running"'s slot frees
                    // up via a genuine Succeeded event, not an incidental GaveUp from
                    // an empty `.part` file failing the final copy.
                    destination.writeBytes(ByteArray(10))
                }
            val queue = newQueue(engine, maxConcurrent = 1)

            queue.enqueue(request("running", priority = 0))
            runCurrent()
            assertTrue(queue.stateOf("running") is DownloadState.Running)

            // Both "low" and "high" are enqueued while the only slot is taken —
            // they sit side by side in the Queued state, and only priority
            // (not enqueue order) should decide who goes next.
            queue.enqueue(request("low", priority = 0))
            queue.enqueue(request("high", priority = 10))
            runCurrent()
            assertEquals(DownloadState.Queued, queue.stateOf("low"))
            assertEquals(DownloadState.Queued, queue.stateOf("high"))

            gates.getValue("https://example.invalid/running").complete(Unit)
            runCurrent()

            assertTrue(queue.stateOf("high") is DownloadState.Running)
            assertEquals(DownloadState.Queued, queue.stateOf("low"))
        }

    @Test
    fun `pause cancels the in-flight transfer and preserves last known progress`() =
        runTest {
            val neverCompletes = CompletableDeferred<Unit>()
            val engine =
                ScriptedEngine { _, _, _, onHeaders, onProgress ->
                    onHeaders(ResponseInfo(100, null, null, true))
                    onProgress(40)
                    neverCompletes.await()
                }
            val queue = newQueue(engine, maxConcurrent = 1)
            val id = queue.enqueue(request("a"))
            runCurrent()
            assertEquals(DownloadState.Running(40L, 100L), queue.stateOf("a"))

            queue.pause(id)
            runCurrent()

            assertEquals(DownloadState.Paused(40L, 100L), queue.stateOf("a"))
        }

    @Test
    fun `resume requeues a paused task and it runs again`() =
        runTest {
            val engine =
                ScriptedEngine { _, resume, destination, onHeaders, onProgress ->
                    onHeaders(ResponseInfo(100, null, null, true))
                    destination.writeBytes(ByteArray(100))
                    onProgress(100)
                }
            val neverCompletes = CompletableDeferred<Unit>()
            val pausingEngine =
                ScriptedEngine { url, resume, destination, onHeaders, onProgress ->
                    if (resume.bytesDownloaded == 0L) {
                        onHeaders(ResponseInfo(100, null, null, true))
                        onProgress(40)
                        neverCompletes.await()
                    } else {
                        engine.download(url, emptyMap(), destination, resume, onHeaders, onProgress)
                    }
                }
            val queue = newQueue(pausingEngine, maxConcurrent = 1)
            val id = queue.enqueue(request("a"))
            runCurrent()
            queue.pause(id)
            runCurrent()
            assertEquals(DownloadState.Paused(40L, 100L), queue.stateOf("a"))

            queue.resume(id)
            runCurrent()

            assertTrue(queue.stateOf("a") is DownloadState.Completed)
        }

    @Test
    fun `cancel marks the task Canceled and deletes its on-disk files`() =
        runTest {
            val neverCompletes = CompletableDeferred<Unit>()
            val engine =
                ScriptedEngine { _, _, destination, onHeaders, onProgress ->
                    onHeaders(ResponseInfo(100, null, null, true))
                    destination.writeBytes(ByteArray(10))
                    onProgress(10)
                    neverCompletes.await()
                }
            val downloadDir = tempFolder.newFolder()
            val store = TaskStore(downloadDir)
            val config = DownloadConfig(downloadDir = downloadDir, maxConcurrent = 1)
            val queue =
                DownloadQueue(
                    scope = backgroundScope,
                    config = config,
                    store = store,
                    engine = engine,
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                )

            val id = queue.enqueue(request("a"))
            runCurrent()
            queue.cancel(id)
            runCurrent()

            assertEquals(DownloadState.Canceled, queue.stateOf("a"))
            assertTrue(!store.metaFile(id).exists())
            assertTrue(!store.partFile(id).exists())
        }

    @Test
    fun `a retryable failure retries with backoff and eventually succeeds`() =
        runTest {
            val attempts = AtomicInteger(0)
            val engine =
                ScriptedEngine { _, _, destination, onHeaders, onProgress ->
                    if (attempts.getAndIncrement() < 2) {
                        throw DownloadError.Network(java.io.IOException("boom"))
                    }
                    onHeaders(ResponseInfo(5, null, null, true))
                    destination.writeBytes("hello".toByteArray())
                    onProgress(5)
                }
            val queue =
                newQueue(
                    engine,
                    maxConcurrent = 1,
                    retryPolicy =
                        DownloadRetryPolicy(
                            maxAttempts = 5,
                            initialDelay = 5.milliseconds,
                            jitterRatio = 0.0,
                        ),
                )

            queue.enqueue(request("a"))
            runCurrent() // attempt 1: fails immediately, then the worker suspends in delay(5ms)
            advanceTimeBy(5.milliseconds)
            runCurrent() // attempt 2: fails, suspends in delay(10ms)
            advanceTimeBy(10.milliseconds)
            runCurrent() // attempt 3: succeeds

            assertEquals(3, attempts.get())
            assertTrue(queue.stateOf("a") is DownloadState.Completed)
        }

    @Test
    fun `exhausting all retry attempts marks the task Failed`() =
        runTest {
            val engine =
                ScriptedEngine { _, _, _, _, _ ->
                    throw DownloadError.Http(500)
                }
            val queue =
                newQueue(
                    engine,
                    maxConcurrent = 1,
                    retryPolicy =
                        DownloadRetryPolicy(
                            maxAttempts = 2,
                            initialDelay = 5.milliseconds,
                            jitterRatio = 0.0,
                        ),
                )

            queue.enqueue(request("a"))
            runCurrent() // attempt 1: fails, suspends in delay(5ms)
            advanceTimeBy(5.milliseconds)
            runCurrent() // attempt 2: fails, retries exhausted — gives up

            assertTrue(queue.stateOf("a") is DownloadState.Failed)
        }

    @Test
    fun `a checksum mismatch after a full transfer marks the task Failed`() =
        runTest {
            val engine =
                ScriptedEngine { _, _, destination, onHeaders, onProgress ->
                    onHeaders(ResponseInfo(5, null, null, true))
                    destination.writeBytes("hello".toByteArray())
                    onProgress(5)
                }
            val queue = newQueue(engine, maxConcurrent = 1)

            val wrongSha256 = "0".repeat(64)
            queue.enqueue(request("a", sha256 = wrongSha256))
            runCurrent()

            val failed = queue.stateOf("a")
            assertTrue(failed is DownloadState.Failed)
            assertTrue((failed as DownloadState.Failed).error is DownloadError.ChecksumMismatch)
        }

    @Test
    fun `re-enqueueing an already-tracked task is idempotent`() =
        runTest {
            val neverCompletes = CompletableDeferred<Unit>()
            val engine =
                ScriptedEngine { _, _, _, onHeaders, onProgress ->
                    onHeaders(ResponseInfo(100, null, null, true))
                    onProgress(50)
                    neverCompletes.await()
                }
            val queue = newQueue(engine, maxConcurrent = 1)

            val firstId = queue.enqueue(request("a"))
            runCurrent()
            val secondId = queue.enqueue(request("a"))
            runCurrent()

            assertEquals(firstId, secondId)
            assertEquals(1, engine.callCount.get())
            assertEquals(DownloadState.Running(50L, 100L), queue.stateOf("a"))
        }
}
