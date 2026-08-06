# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

Two things live in one repo, on purpose, and must stay decoupled:

1. **The SDK: 19 published modules** — `:updatechecker` plus 18 `core-*`/`feature-*`/`-hilt` companion/`:sdk-bom` modules, all published to JitPack under `com.github.sanatowhite.sdk:<module>:<version>` (see ADR 0008). Real, independently-versioned AARs a consumer `implementation(...)`s — never source they copy and edit. `:updatechecker` in particular keeps zero third-party deps beyond `core-ktx`/`coroutines-android`, zero internal module deps, additive-only public API.
2. **`:app` + `:benchmark` + `:baselineprofile`** — a fork-able Android app template: Compose + Hilt + Navigation, performance monitoring, wired up entirely from the published SDK modules above via `project(...)` dependencies (see `docs/adr/` for why `project()` and not the Maven coordinates, even though `:app` lives in the same repo). Someone forks this repo, runs `scripts/bootstrap.sh`, and gets a runnable app.

These two halves are published on **different tag namespaces** (bare semver `*.*.*` for the SDK, `app-v*` for the app) and must never develop a dependency in either direction — an SDK module depending on `:app` would be a `verifyModuleGraph` failure by construction (`:app` isn't in the publishable set).

## Commands

```bash
# SDK-only build (mirrors what JitPack actually runs)
JITPACK=true ./gradlew publishSdkToMavenLocal -Pversion=probe
find ~/.m2/repository/com/github/sanatowhite/sdk -type f | sort   # eyeball the actual published artifact set

# Full template build
./gradlew :app:assembleDebug :app:assembleRelease
./gradlew testDebugUnitTest                          # all modules
./gradlew :updatechecker:test                        # SDK module, run in isolation too
./gradlew lintDebug spotlessCheck verifyModuleGraph  # required PR-check gates
./gradlew verifyRoborazziDebug                        # screenshot baselines (fails if UI drifted)
./gradlew recordRoborazziDebug                        # re-record baselines after an intentional UI change
./gradlew detekt koverHtmlReport                      # advisory only, not gating
./gradlew apiCheckAll                                 # binary-compatibility gate across every SDK module that has one (core-ui + Compose-heavy feature-* + :sdk-bom excluded, see build.gradle.kts)
./gradlew :updatechecker:apiDump                      # regenerate one module's API snapshot after an intentional (additive) change; :<module>:apiCheck/apiDump works for any module with sanato.api.check applied

# Single test class
./gradlew :core-net:testDebugUnitTest --tests "*.RetryInterceptorTest"

# Baseline profile / benchmark (needs a physical device or API 28+ emulator; NOT reliable on shared CI for numbers)
./gradlew :app:generateReleaseBaselineProfile          # regenerates app/src/release/generated/baselineProfiles/*.txt — commit the result, it's a build input
./gradlew :benchmark:connectedBenchmarkAndroidTest      # macrobenchmark smoke test, not a perf gate

# Dependency graph / module boundaries / publish-set consistency
./gradlew verifyModuleGraph                           # fails the build if a module violates the allowed dependency graph below
./gradlew verifySdkModuleList                         # fails if sdkModules (below) drifts from settings.gradle.kts's include list
./gradlew verifySdkBomConstraints                     # fails if sdk-bom's constraint list drifts from sdkModules
./scripts/dep-graph.sh :app                            # human-readable dependency tree for one module

# Consumer-side verification — a genuinely separate Gradle build, see checks/consumer-smoke/README.md
(cd checks/consumer-smoke && ./gradlew :app:assembleDebug -PsmokeVersion=probe)   # after publishSdkToMavenLocal -Pversion=probe above
```

JDK must be **17** (not 21, not the system default). If `./gradlew` can't find it locally, `gradle/gradle-daemon-jvm.properties` pins the daemon toolchain; for ad-hoc shell use, `export JAVA_HOME=".../Android Studio.app/Contents/jbr/Contents/Home"` (do not commit this).

## Architecture

### Module graph (enforced by `verifyModuleGraph`, not just convention)

Four tiers, 19 modules (see ADR 0008 for why the shape is what it is):

```
Tier 1 (capability, zero Hilt, zero glue between each other):
  :core-common    → (nothing)
  :core-init      → (nothing)
  :core-ui        → :core-common
  :core-net       → :core-common
  :core-data      → :core-common
  :core-telemetry → :core-common, :core-init

Tier 3 (Hilt assembly — the only tier allowed to glue across Tier-1 boundaries):
  :core-common-hilt    → :core-common
  :core-init-hilt      → :core-init
  :core-data-hilt      → :core-data
  :core-telemetry-hilt → :core-telemetry, :core-init-hilt, :core-common
  :net-telemetry-hilt  → :core-net, :core-telemetry   (the one cross-Tier-1 bridge)
  :telemetry-firebase  → :core-telemetry

Tier 2 (standard pages — apply Hilt directly themselves, unlike Tier 1):
  :feature-settings → :core-common, :core-ui, :core-data, :core-data-hilt, :core-common-hilt
  :feature-feedback → :core-common, :core-telemetry, :core-ui, :core-common-hilt
  :feature-licenses → :core-ui
  :feature-update   → :updatechecker, :core-ui

Tier 0 / other:
  :updatechecker → (nothing — hard rule, see below)
  :debug-tools   → :core-telemetry
  :sdk-bom       → (pure version constraints, no project() deps at all)

:app → all of the above via project(...)
:benchmark / :baselineprofile → only via targetProjectPath to :app
```

`:core-ui` never depends on `:core-net`/`:core-data` — a UI component library that can pull in the network stack would slow down every screenshot test and blur where wiring happens. `verifyModuleGraph` also enforces a publish-correctness rule: **a publishable module must not depend on a non-publishable one** (would put an unresolvable coordinate in a published POM). Run `./gradlew verifyModuleGraph` after changing any module's dependencies; it inspects the actual `implementation`/`api` configurations, not just this diagram — and this diagram has to be kept in sync with the map in `build.gradle.kts` by hand (see ADR 0003).

Two sibling drift-checks guard the other hand-written lists this graph depends on: `verifySdkModuleList` (the `sdkModules` array in `build.gradle.kts` vs. which subprojects actually apply `maven-publish`) and `verifySdkBomConstraints` (`sdk-bom`'s constraint list vs. `sdkModules`).

### The four `:updatechecker` rules

`:updatechecker` predates the app template, and unlike every other SDK module, has never applied any `build-logic` convention plugin. It now shares the same publish set, tag, and version as the other 18 modules (ADR 0008) rather than being mirrored out separately, but its own four rules are unchanged in spirit:

1. **Zero internal module dependencies** — the general "publishable module must not depend on a non-publishable one" rule from `verifyModuleGraph` applies to every SDK module, but `:updatechecker` is the strictest case: it must have **zero** project dependencies at all, publishable or not.
2. **Zero third-party dependencies beyond `androidx.core:core-ktx` (kept `implementation` — see ADR 0009 on why the `FileProvider` superclass doesn't leak) and `kotlinx-coroutines-android` (kept `api` — a genuine leak via `UpdateDownloader.download(): Flow<...>`).** Don't "helpfully" swap in OkHttp; that forces OkHttp onto every consumer.
3. **Applies only the two purely-additive publishing mix-ins** (`sanato.android.library.published`, `sanato.api.check`), never `sanato.android.library` itself or any other convention plugin. Those two are structurally incapable of injecting a dependency or changing `consumerProguardFiles`/`namespace` — see `SanatoPublishedLibraryConventionPlugin.kt`'s own doc comment for why that's true by construction, not by care. Anything else risks silently changing the published artifact.
4. **Only additions, never removals or signature changes**, to its public API — same `apiCheck` gate every other non-Compose-heavy SDK module now has (`sanato.api.check`, ADR 0009), just applied here first. Java bytecode target stays 11 for **every** published module now (not just `:updatechecker` — see the Java version note below), since consumers may still be on older AGP.

If you touch anything under `updatechecker/`, run `./gradlew :updatechecker:test :updatechecker:apiCheck` before anything else.

### DI boundary

`core-*` (Tier 1) modules only use `javax.inject` annotations — no Hilt, no `@Module`/`@Component` anywhere. Default Hilt wiring for each one lives in a separate published `-hilt` companion module (`core-common-hilt`, `core-init-hilt`, `core-data-hilt`, `core-telemetry-hilt`, `net-telemetry-hilt`, plus `telemetry-firebase`) — this split exists because Hilt's `@Module @InstallIn(...)` installs unconditionally the moment its declaring module is on the classpath **and has itself run `hilt-compiler`**; see `docs/adr/spike-0000-hilt-library-module-aggregation.md` for the empirical spike behind that claim, and ADR 0008/0004 for the full reasoning. Tier-2 `feature-*` modules apply Hilt directly themselves (their Route composables require it). `:app/di/` still exists, but now only for genuinely app-specific bindings (the `@Binds @IntoSet` initializer entries in `InitializerModule.kt`) — everything reusable moved into a `-hilt` module. This split means a consumer can take a capability module's Hilt wiring or `exclude()` it and provide their own binding (documented per-module in each `-hilt` module's README), without dragging Hilt onto the capability module itself.

### `Telemetry` abstraction (`:core-telemetry`)

- Collectors never self-register (`AppInitializers` in `:app` drives startup order explicitly, split into `@Eager`/`@Deferred`), except the crash handler and cold-start timer, which must run in `attachBaseContext`, before DI exists.
- Never call a `Telemetry` backend from inside the crash handler itself — that double-reports (fatal + non-fatal). The handler only writes a small file synchronously; the actual report happens on next launch.
- `Set<Telemetry>` must have an explicit `@Multibinds` binding (`core-telemetry-hilt`) — Hilt treats an empty set as a compile error otherwise. Firebase is `:app`'s default backend now (unconditional `implementation(project(":telemetry-firebase"))`, no feature flag — see ADR 0006/0008), but the `@Multibinds` requirement is what still makes "zero *other* backends registered" a valid, compiling empty-set-minus-one rather than a Hilt error.
- Do not initialize anything via `androidx.startup` — it registers its own `ContentProvider`, which pollutes the exact cold-start window this module measures.
- `Debug.MemoryInfo` sampling happens only at specific lifecycle moments, never on a timer — it's expensive and rate-limited by the OS on API 29+.

### Feature flags

`sealed class FlagKey<T>` + a central `AppFlags.all` registry (not an enum — flags carry different value types). Priority: `LocalOverride (debug only) > Remote > Cache > Default`. The debug-only override is contributed via `@IntoSet` only in the debug source set, so release builds are physically incapable of accepting a local override.

### Build-logic

`build-logic/convention` is a composite build of Gradle precompiled script plugins (mostly `.gradle.kts`, with `:app`'s specific one written as a class-based plugin — see `SanatoAndroidApplicationConventionPlugin.kt` — since it needs to parameterize signing/build types/applicationId in one place). AGP 9's built-in-Kotlin model means **no module applies `org.jetbrains.kotlin.android`** — Kotlin is a runtime dependency of AGP, not a plugin you apply. Compose still needs its own compiler plugin applied explicitly (`org.jetbrains.kotlin.plugin.compose`); built-in Kotlin does not replace it.

Two of these plugins are pure additive mix-ins for publishing, not full library-module conventions: `sanato.android.library.published` (`maven-publish` + release single-variant + sources jar + POM metadata + group/version from `gradle/version.properties`) and `sanato.api.check` (`apiDump`/`apiCheck` tasks backed by `ApiSnapshotTask.kt`, a real incremental `DefaultTask`). Both are structurally incapable of adding a dependency or touching `defaultConfig`/`consumerProguardFiles` — that's the whole reason `:updatechecker` can apply them without violating its "no convention plugin" rule (see above).

### Debug-only code, zero release residue

`:debug-tools` is `debugImplementation` only, with `app/src/{debug,release,staging}` facade files providing the same `@Composable` entry point (`DebugOverlay`) — release/staging's version is an inline no-op. CI's `release-smoke` job asserts via `apkanalyzer dex packages` that no debug-only symbols leak into the release APK; don't rely on `debugImplementation` alone without that check.

### Signing

`android.enableR8.fullMode` stays default (full mode). Debug/staging/release use different `applicationId` suffixes (`.debug`, `.staging`, none) so all three can be installed side by side on one device — this is what makes the update-check flow testable locally. Missing `keystore.properties` degrades to debug signing with a build-time warning rather than failing — don't make that a hard error, forks need `assembleRelease` to work out of the box.

### Testing

- Robolectric is pinned to API 35 in `robolectric.properties` (API 36 needs JDK 21; this repo standardizes on JDK 17). Existing `@Config(sdk = [34])` annotations on specific test classes take priority and should not be "cleaned up" to match the default.
- Roborazzi screenshot baselines are recorded in CI only (`screenshot-record.yml`, manual trigger), never locally — local machine's font rendering differs from the CI container's.
- `release-smoke` (assembleRelease) is a **required** PR check, not optional — the R8 + `kotlinx.serialization` keep rules for Retrofit and the type-safe Navigation routes only break in a minified build; debug builds hide this class of bug completely.

### Bootstrap script (`scripts/bootstrap.sh`)

Renames `io.sanato.apptemplate` → the fork's own applicationId everywhere (packages, `namespace`/`applicationId` — including the one hardcoded in `SanatoAndroidApplicationConventionPlugin.kt`, which is why `build-logic/` is **not** excluded from the Step 2 text sweep, only from Step 1's directory move, which has nothing to rename there anyway — manifest, proguard, `google-services.json`, baseline profile JVM descriptors, docs). It is designed so that `io.sanato.apptemplate` diverges from every published module's namespace at the third path segment — `io.sanato.updatechecker` (the standalone SDK) and `io.sanato.appkit.*` (all 18 other SDK modules — see ADR 0008). The replacement token is always three segments, so even if a path-exclude glob were wrong, no published module could be accidentally rewritten; `PUBLISHED_MODULES` in the script (must match the root `build.gradle.kts` `sdkModules` list) also carries an explicit per-module `git status --porcelain` assertion as defense in depth, not reliance on the token happening to be absent. `gradle/version.properties` is updated line-by-line (`versionCode=`/`versionName=` only via `perl -pi`), never overwritten wholesale — that file also carries `sdkGroup=`/`sdkVersion=` (ADR 0008), which a full-file overwrite would silently delete and break every module applying `sanato.android.library.published`. Any assertion failure inside the script rolls back via `git reset --hard HEAD && git clean -fd` on the branch being abandoned, then switches back to `main` and deletes the branch — `git switch` alone does not discard staged changes made on the branch you're leaving.

Test changes to this script only inside an isolated clone/copy in a scratch directory, never against this working tree — and test against the repo's actual **current** state (including uncommitted changes, if any), not just the last commit, since a plain `git clone` from local `HEAD` silently skips whatever hasn't been committed yet.
