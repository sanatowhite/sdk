# 0009 — The `api()` vs `implementation()` judgment rule for published modules

## Context

Before this repository published anything beyond `:updatechecker`, every inter-module Gradle dependency in it was `implementation` — harmless, because nothing outside the repo ever compiled against these modules' public signatures. The moment `core-ui`/`core-net`/`core-data`/the `feature-*` modules became real Maven artifacts (ADR 0008), that stopped being true: a dependency declared `implementation` is invisible on a **consumer's compile classpath** (Gradle only propagates `api` dependencies transitively for compilation; `implementation` is present at runtime but not resolvable by the consumer's own source). Every one of these modules' public signatures is full of Compose, OkHttp, Retrofit, DataStore, and kotlinx.serialization types — get the `api`/`implementation` call wrong on any of them and a consumer's code fails to compile with an "unresolved reference" that has nothing to do with anything they wrote.

Getting this right by inspection, module by module, is exactly the kind of judgment call that erodes under time pressure ("it compiles in `:app` via `project()`, ship it") — `project()` dependencies inside the same build make the distinction invisible, because Gradle's project substitution resolves them identically either way during this repo's own build. The only thing that actually exercises the `api`/`implementation` boundary the way a real consumer would is `checks/consumer-smoke` (ADR 0008), which depends on published coordinates, not `project(...)`.

## Decision

A dependency must be declared `api` **if and only if** its type appears in the module's own ABI:

- a `public`/`protected` function or property's parameter, return, or backing type;
- the superclass or superinterface of a `public` type;
- an annotation (including through its meta-annotation chain) applied to a `public` type or member;
- any type referenced inside a `public inline` function's body — the body is copied verbatim into the consumer's compiled bytecode, so whatever it references becomes the consumer's problem to resolve too.

Everything else — types used only inside private/internal implementation, or inside a function body that is **not** `inline` — stays `implementation`. This holds even when the type looks superficially load-bearing, with two judgment calls worth recording explicitly because they were easy to get wrong in practice:

- **Kotlin `internal` is a compile-time-only restriction; the class is still `public` bytecode.** `:updatechecker`'s `UpdateCheckerFileProvider extends androidx.core.content.FileProvider` looks like a classic superclass leak. It isn't: the class is declared `internal`, so consumer *source code* can never reference it — its only real caller is the Android framework, instantiating it reflectively via the `<provider>` tag in the manifest, which is a runtime classpath need, not a compile-time ABI one. `androidx-core-ktx` correctly stays `implementation`; only `kotlinx-coroutines-android` needed `api` there (a genuine leak, via `UpdateDownloader.download(): Flow<UpdateDownloadState>`).
- **A `public inline` function's body is not exempt just because it "only" catches exceptions.** `core-net`'s original `safeApiCall` was `suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T)`, whose body caught `HttpException`/`SerializationException` — both would have had to be `api` for the inlined bytecode to resolve at every call site. Removing `inline`/`crossinline` (the function has no measurable performance need for inlining) was cheaper than permanently pinning two exception types into the public ABI, and was a deliberate choice made specifically because this was the last point before publishing where the trade was still free to make.
- A **Compose BOM must be `api`, not just the individual Compose artifacts**: if a module's own BOM constraint isn't visible to the consumer, the consumer's own (possibly different) Compose BOM wins arbitration, and the module's actual runtime Compose versions can silently diverge from what it was built and tested against. `:core-ui` makes this explicit.

## Consequences

- Every module's `build.gradle.kts` carries an inline comment next to each `api(...)`/`implementation(...)` line stating *which* public signature justifies the choice — this ADR records the rule; the per-line comments are the audit trail for each individual application of it, and are expected to be the first thing checked when adding a new dependency.
- `core-ui` and the four Compose-heavy `feature-*` modules deliberately do **not** enable `apiCheck` (see `SanatoApiCheckConventionPlugin.kt`, ADR 0008's Tier-2 description): the Compose compiler injects `Composer`/`$changed`/`$default` synthetic parameters into every `@Composable` function's bytecode signature, and those drift with the Compose compiler version independent of any real API change — a `javap`-based snapshot diff on them is noise, not signal. Their API stability is instead verified by whether `checks/consumer-smoke` still compiles against them.
- This rule is necessary but not sufficient — it catches "did I forget to promote a dependency," not "did I accidentally change a public signature's meaning." The latter is `sanato.api.check`'s job (golden-file `javap`-style snapshots, `apiDump`/`apiCheck`) for every module that isn't Compose-heavy, and `checks/consumer-smoke` for everything, Compose-heavy or not.
