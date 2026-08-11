# CLAUDE.md — `:downloadkit`

Directory-scoped guidance for Claude Code when working inside this module.
Only things specific to this module — JDK version, spotless, the four gate
commands live in the root `CLAUDE.md`.

## What this module is, and its boundary

Resumable download engine: HTTP Range resume, a bounded-concurrency task
queue, on-disk checkpointing (survives process death), and an optional
foreground-service + notification layer. `Downloader.getInstance(context)` is
the only entry point a consumer touches. See `README.md`'s "这是什么/不是什么"
for the full boundary.

## Dependency direction — the one thing narrower than `verifyModuleGraph`'s rule

This is the **first** `*kit` module (`:updatechecker`/`:backupkit`/`:logkit`
are the others) that is *not* zero-internal-dependency — it depends on
`:core-net` for OkHttp. That's a deliberate, reviewed exception
(`docs/adr/0013-downloadkit-depends-on-core-net.md`), not a precedent to
casually extend. If you're tempted to add a second dependency here, re-read
ADR 0013's reasoning first — the bar for "why does a `*kit` module need this"
is high on purpose.

## Concurrency model — read before touching `queue/DownloadQueue.kt`

Single-consumer-channel design, same discipline as `:core-net`'s
`ws.RealWebSocketConnection`: one `events: Channel<Event>` serializes every
command (`enqueue`/`pause`/`resume`/`cancel`) *and* every worker callback
(`Headers`/`Progress`/`Succeeded`/`AttemptFailed`/`GaveUp`) into a single loop
coroutine that owns `tasksById`/`activeWorkers`. Never add a second place that
mutates those maps — route through an `Event` instead, even if it feels like
overkill for a "trivial" change. `Entry.generation` exists to make stale
worker events (from a paused/canceled/superseded attempt) safely ignorable —
don't remove the generation check "because it looks redundant" without tracing
through what happens when a worker's terminal event arrives after a `pause()`
already tore it down.

## Testing quirks

- **`advanceUntilIdle()` does not reliably wake a `backgroundScope.launch { for (x in channel) {...} }` consumer in this repo's kotlinx-coroutines-test setup** — confirmed empirically, not documented upstream. Use `runCurrent()` after every `enqueue`/`pause`/`resume`/`cancel`/`trySend` in `DownloadQueue` tests instead (see `queue/DownloadQueueTest.kt`). For backoff-delay assertions, pair `advanceTimeBy(duration)` with `runCurrent()`.
- `DownloadQueue`/`OkHttpDownloadEngine` take an injectable `ioDispatcher: CoroutineDispatcher = Dispatchers.IO` specifically so tests can pass `StandardTestDispatcher(testScheduler)` — blocking file I/O (`TaskStore.save`, checksum verification, the final file copy) otherwise silently escapes onto a real thread pool that the virtual scheduler can't see or wait for.
- `Downloader` is a real process-wide singleton (`companion.instance`). Robolectric only spins up a fresh classloader when `@Config` actually differs between test methods in the same class — otherwise the singleton persists across methods. Any test calling `Downloader.getInstance(...)` must call `Downloader.resetForTesting()` in `@After`, or a later test silently inherits an earlier test's `Downloader` pointed at an already-deleted `TemporaryFolder` directory.
- `AndroidDownloadNotifier`/`DownloadService` tests need `testOptions.unitTests.isIncludeAndroidResources = true` (already set in `build.gradle.kts`) — they read real string resources under Robolectric.

## The one command after touching this module

```bash
./gradlew :downloadkit:test :downloadkit:apiCheck :downloadkit:lintDebug
```

If you touched `engine/OkHttpDownloadEngine.kt`, also run the Range-resume
suite in isolation — it's the highest-value test in this module:

```bash
./gradlew :downloadkit:testDebugUnitTest --tests "*.engine.OkHttpDownloadEngineTest"
```
