# 0013 — `:downloadkit` depends on `:core-net`, breaking the zero-dependency `*kit` pattern

## Context

The repo has three self-contained `*kit` modules — `:updatechecker`,
`:backupkit`, `:logkit` — each with a hard "zero internal module dependencies"
rule (see root `CLAUDE.md`'s per-module rule sections). `:updatechecker` and
`:backupkit-drive` (Drive REST) both do their HTTP work with bare
`HttpURLConnection`, and that precedent was the starting assumption for a new
resumable-download library: keep it zero-dependency like its siblings.

That assumption didn't survive contact with the actual requirement. Resumable
downloads need:

1. A **streaming** response body — the file being downloaded can be
   arbitrarily large, and `HttpURLConnection`'s stream handling combined with
   hand-rolled connection reuse/keep-alive is exactly the surface OkHttp
   exists to get right once.
2. Real **connection pooling** — a bounded-concurrency queue (`maxConcurrent`)
   opening and tearing down a raw `HttpURLConnection` per attempt, per retry,
   per resumed task, would either leak sockets or reinvent OkHttp's dispatcher
   and connection pool from scratch.
3. **TLS session reuse** across many short-lived requests (a resumed transfer
   is, from the network's perspective, N separate requests) — again something
   OkHttp already does correctly.

Rewriting all three from scratch, in a security-sensitive area (TLS), to
preserve a "zero dependency" property that exists for a different reason
(keeping `:updatechecker`'s published surface minimal for a downstream
`REQUEST_INSTALL_PACKAGES` flow) was judged not worth it.

## Decision

`:downloadkit` depends on `:core-net` (`api(project(":core-net"))`) for
`HttpClientFactory`/`OkHttpClient`, and is the **only** `*kit` module that
does. This is a one-time, reviewed exception — not a precedent that lowers the
bar for the next `*kit` module. Specifics:

- **`OkHttpClient` appears in `Downloader`'s public signature** (the
  `downloadOkHttpClient(base: OkHttpClient)` factory and the `getInstance`
  overload that accepts a custom client), so `:core-net` must be `api`, not
  `implementation` — same ADR 0009 reasoning as `:auth-net-hilt`'s
  `@Authenticated OkHttpClient` binding.
- **`HttpClientFactory.okHttpClient()`'s `callTimeout(30s)`/`readTimeout(15s)`
  are fatal to any transfer over a few seconds.** `Downloader.downloadOkHttpClient()`
  derives a new client (`callTimeout(Duration.ZERO)`, `readTimeout(Duration.ZERO)`)
  rather than mutating the frozen `okHttpClient()` signature — the same
  derive-don't-mutate pattern `ws.WebSocketFactory.webSocketOkHttpClient()`
  established for the WebSocket case (ADR 0012). `RetryInterceptor` is also
  stripped from the derived client: its blocking `Thread.sleep` retries would
  stack with `queue.DownloadQueue`'s own attempt/backoff policy
  (`DownloadRetryPolicy`), multiplying retries and holding an OkHttp
  dispatcher thread hostage for the interceptor's sleep on top of it.
- **Checksum verification does not reuse `:updatechecker`'s `Sha256Verifier`.**
  That class lives in a zero-internal-dependency module; depending on it (or
  vice versa) would violate `:updatechecker`'s own rule #1. `:downloadkit`
  hand-rolls a small `Sha256` object instead — a few lines duplicated is
  cheaper than coupling two modules that are each supposed to be
  independently mirrorable/publishable in isolation.
- **`:downloadkit-hilt` inherits the same dependency shape** as
  `:auth-net-hilt`/`:net-telemetry-hilt`: `api(:downloadkit)`,
  `implementation(:core-net)` (assembling the client), `implementation(:core-common)`
  (`isDebuggableBuild()`). No new cross-Tier-1 bridge concept is introduced —
  `:downloadkit`/`:downloadkit-hilt` sit in the same "other" bucket as
  `:updatechecker`/`:backupkit` in the module graph, just with one outgoing
  edge instead of zero.

## Consequences

- **Cost accepted, not overlooked**: the published `:downloadkit` POM now
  transitively pulls OkHttp + Retrofit + kotlinx-serialization-json (all
  already `api` from `:core-net`). A consumer who wants byte-for-byte minimal
  downloader dependencies (à la `:updatechecker`'s zero-dependency guarantee)
  does not have that option with `:downloadkit`. This is the deliberate
  trade-off this ADR records.
- `verifyModuleGraph`'s `allowedProjectDeps` gains `":downloadkit" to setOf(":core-net")`
  and `":downloadkit-hilt" to setOf(":downloadkit", ":core-net", ":core-common")`
  — both publishable-depends-on-publishable, no `verifyModuleGraph` exception
  needed.
- The next candidate `*kit` module that's tempted to add a `:core-net`
  dependency should not treat this ADR as a green light by default — re-derive
  the streaming/pooling/TLS-reuse argument for its own use case first. A
  module that just wants JSON parsing or a single non-streaming request
  should still default to zero dependencies, matching `:updatechecker`'s
  `HttpConfigFetcher` precedent.
- Two new published modules join `sdkModules`: `:downloadkit` (depends on
  `:core-net`) and `:downloadkit-hilt` (Hilt wiring, mirrors
  `:auth-net-hilt`'s shape). Module count: 25 → 27.
