# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

Two things live in one repo, on purpose, and must stay decoupled:

1. **`:updatechecker`** — a standalone Android library (in-app update checking) published to JitPack as `com.github.sanatowhite:version-check-sdk`. Zero third-party dependencies, zero internal module dependencies, frozen public API.
2. **Everything else** — a fork-able Android app template (`android-app-template`): Compose + Hilt + Navigation, performance monitoring, standard pages (settings/about/consent/feedback), CI release pipeline. Someone forks this repo, runs `scripts/bootstrap.sh`, and gets a runnable app.

These two halves are published on **different tag namespaces** (`v*` for the SDK, `app-v*` for the app) and must never develop a dependency in either direction.

## Commands

```bash
# SDK-only build (mirrors what JitPack actually runs)
JITPACK=true ./gradlew :updatechecker:publishToMavenLocal -Pgroup=com.github.sanatowhite -Pversion=probe
./gradlew :updatechecker:test                      # 19/19, no Robolectric-version surprises

# Full template build
./gradlew :app:assembleDebug :app:assembleRelease
./gradlew testDebugUnitTest                          # all modules
./gradlew :updatechecker:test                        # SDK module, run in isolation too
./gradlew lintDebug spotlessCheck verifyModuleGraph  # required PR-check gates
./gradlew verifyRoborazziDebug                        # screenshot baselines (fails if UI drifted)
./gradlew recordRoborazziDebug                        # re-record baselines after an intentional UI change
./gradlew detekt koverHtmlReport                      # advisory only, not gating
./gradlew :updatechecker:apiCheck                     # binary-compatibility gate on the SDK's public API
./gradlew :updatechecker:apiDump                      # regenerate the API snapshot after an intentional (additive) change

# Single test class
./gradlew :core-net:testDebugUnitTest --tests "*.RetryInterceptorTest"

# Baseline profile / benchmark (needs a physical device or API 28+ emulator; NOT reliable on shared CI for numbers)
./gradlew :app:generateReleaseBaselineProfile          # regenerates app/src/release/generated/baselineProfiles/*.txt — commit the result, it's a build input
./gradlew :benchmark:connectedBenchmarkAndroidTest      # macrobenchmark smoke test, not a perf gate

# Dependency graph / module boundaries
./gradlew verifyModuleGraph                           # fails the build if a module violates the allowed dependency graph below
./scripts/dep-graph.sh :app                            # human-readable dependency tree for one module
```

JDK must be **17** (not 21, not the system default). If `./gradlew` can't find it locally, `gradle/gradle-daemon-jvm.properties` pins the daemon toolchain; for ad-hoc shell use, `export JAVA_HOME=".../Android Studio.app/Contents/jbr/Contents/Home"` (do not commit this).

## Architecture

### Module graph (enforced by `verifyModuleGraph`, not just convention)

```
:app → :core-ui, :core-data, :core-net, :core-telemetry, :core-common, :updatechecker
:core-ui        → :core-common
:core-data      → :core-net, :core-common
:core-net       → :core-common
:core-telemetry → :core-common
:core-common    → (nothing)
:updatechecker  → (nothing — hard rule, see below)
:benchmark / :baselineprofile → only via targetProjectPath to :app
```

`:core-ui` never depends on `:core-net`/`:core-data` — a UI component library that can pull in the network stack would slow down every screenshot test and blur where wiring happens (wiring only happens in `:app`). Run `./gradlew verifyModuleGraph` after changing any module's dependencies; it inspects the actual `implementation`/`api` configurations, not just a design doc.

### The four `:updatechecker` rules

`:updatechecker` predates the app template and is published independently. Every one of these has bitten this codebase before or would break real consumers if violated:

1. **Zero internal module dependencies.** Adding `implementation(project(":core-net"))` breaks the JitPack SDK-only build instantly (that module is gated out of `settings.gradle.kts` when `JITPACK=true`) and pollutes the published POM with coordinates consumers can't resolve.
2. **Zero third-party dependencies** — `HttpURLConnection` + `org.json` + `AlertDialog` only. Don't "helpfully" swap in OkHttp; that forces OkHttp onto every consumer.
3. **No convention plugin.** It intentionally does not apply any `build-logic` plugin. Applying one is very likely to silently add Compose/`javax.inject`, or change `consumerProguardFiles`/`namespace` — all of which change the published artifact. Keep its `build.gradle.kts` minimal and self-contained.
4. **Only additions, never removals or signature changes**, to its public API. `apiCheck` fails the build on any diff; review the diff is purely additive, then run `apiDump` to accept it. Java bytecode target stays 11 (not 17) — consumers may still be on older AGP.

If you touch anything under `updatechecker/`, run `./gradlew :updatechecker:test :updatechecker:apiCheck` before anything else.

### DI boundary

`core-*` modules only use `javax.inject` annotations — no Hilt. All `@Module`/`@Component` wiring lives in `:app/di/`. This means a single `core-*` module can be lifted into a project that uses a different DI framework (or none) without dragging Hilt along.

### `Telemetry` abstraction (`:core-telemetry`)

- Collectors never self-register (`AppInitializers` in `:app` drives startup order explicitly, split into `@Eager`/`@Deferred`), except the crash handler and cold-start timer, which must run in `attachBaseContext`, before DI exists.
- Never call a `Telemetry` backend from inside the crash handler itself — that double-reports (fatal + non-fatal). The handler only writes a small file synchronously; the actual report happens on next launch.
- `Set<Telemetry>` must have an explicit `@Multibinds` binding — Hilt treats an empty set as a compile error otherwise, which is the whole point of the "Firebase off by default" story.
- Do not initialize anything via `androidx.startup` — it registers its own `ContentProvider`, which pollutes the exact cold-start window this module measures.
- `Debug.MemoryInfo` sampling happens only at specific lifecycle moments, never on a timer — it's expensive and rate-limited by the OS on API 29+.

### Feature flags

`sealed class FlagKey<T>` + a central `AppFlags.all` registry (not an enum — flags carry different value types). Priority: `LocalOverride (debug only) > Remote > Cache > Default`. The debug-only override is contributed via `@IntoSet` only in the debug source set, so release builds are physically incapable of accepting a local override.

### Build-logic

`build-logic/convention` is a composite build of Gradle precompiled script plugins (mostly `.gradle.kts`, with `:app`'s specific one written as a class-based plugin — see `SanatoAndroidApplicationConventionPlugin.kt` — since it needs to parameterize signing/build types/applicationId in one place). AGP 9's built-in-Kotlin model means **no module applies `org.jetbrains.kotlin.android`** — Kotlin is a runtime dependency of AGP, not a plugin you apply. Compose still needs its own compiler plugin applied explicitly (`org.jetbrains.kotlin.plugin.compose`); built-in Kotlin does not replace it.

### Debug-only code, zero release residue

`:debug-tools` is `debugImplementation` only, with `app/src/{debug,release,staging}` facade files providing the same `@Composable` entry point (`DebugOverlay`) — release/staging's version is an inline no-op. CI's `release-smoke` job asserts via `apkanalyzer dex packages` that no debug-only symbols leak into the release APK; don't rely on `debugImplementation` alone without that check.

### Signing

`android.enableR8.fullMode` stays default (full mode). Debug/staging/release use different `applicationId` suffixes (`.debug`, `.staging`, none) so all three can be installed side by side on one device — this is what makes the update-check flow testable locally. Missing `keystore.properties` degrades to debug signing with a build-time warning rather than failing — don't make that a hard error, forks need `assembleRelease` to work out of the box.

### Testing

- Robolectric is pinned to API 35 in `robolectric.properties` (API 36 needs JDK 21; this repo standardizes on JDK 17). Existing `@Config(sdk = [34])` annotations on specific test classes take priority and should not be "cleaned up" to match the default.
- Roborazzi screenshot baselines are recorded in CI only (`screenshot-record.yml`, manual trigger), never locally — local machine's font rendering differs from the CI container's.
- `release-smoke` (assembleRelease) is a **required** PR check, not optional — the R8 + `kotlinx.serialization` keep rules for Retrofit and the type-safe Navigation routes only break in a minified build; debug builds hide this class of bug completely.

### Bootstrap script (`scripts/bootstrap.sh`)

Renames `io.sanato.apptemplate` → the fork's own applicationId everywhere (packages, `namespace`/`applicationId`, manifest, proguard, baseline profile JVM descriptors, docs). It is designed so that `io.sanato.apptemplate` and `io.sanato.updatechecker` diverge at the third path segment — the replacement token is always three segments, so even if a path-exclude glob were wrong, the SDK module could not be accidentally rewritten. Any assertion failure inside the script rolls back via `git reset --hard HEAD && git clean -fd` on the branch being abandoned, then switches back to `main` and deletes the branch — `git switch` alone does not discard staged changes made on the branch you're leaving.

Test changes to this script only inside an isolated `git clone` in a scratch directory, never against this working tree.
