# 0006 — Pluggable `Telemetry` abstraction with Firebase as an optional, off-by-default backend

> ⚠️ **Superseded (partially).** The "off-by-default" half of this decision no longer
> holds: `:app` now depends on `:telemetry-firebase` unconditionally and ships a real,
> working `app/google-services.json` for a shared demo project (`sanato-app-template`
> — also `:feature-auth`'s login backend, see ADR 0012) so it builds/runs/reports/logs-in
> out of the box; forking developers should swap in their own `google-services.json`
> before shipping anything real (see TEMPLATE.md). The `telemetryFirebaseEnabled`
> gradle property and the conditional-`buildscript`-classpath mechanism described below
> are gone. The rest of this ADR (the `Telemetry` interface shape, `CompositeTelemetry`/
> `SamplingTelemetry`, `@Multibinds` requirement, no self-registration, no
> `androidx.startup`) is still accurate. See ADR 0008 for the replacement decision
> (Firebase-by-default is one facet of that broader SDK-publishing rework).

## Context

The template needs to collect performance/crash/ANR data usefully, but a fork of this template should not be forced to create a Firebase project, ship `google-services.json`, or make any network calls to Google's backend just to compile and run. At the same time, someone who *does* want Firebase shouldn't have to write the Analytics/Crashlytics wiring from scratch.

A secondary constraint: whichever mechanism turns collectors on at app startup must not itself distort the cold-start measurements those collectors are trying to produce. `androidx.startup:startup-runtime`, a common choice for "auto-initialize this library," works by registering its own `ContentProvider` — which runs during the exact `attachBaseContext`-to-`Application.onCreate` window this template's cold-start timer is measuring. Using it here would mean instrumenting the measurement with the thing doing the measuring.

## Consequences considered and rejected:
- **Reflection-based backend selection** (look up a class by string at runtime, fall back if absent): rejected because a wiring mistake becomes a silent no-op at runtime instead of a compile error.
- **`androidx.startup` for initialization ordering**: rejected for the reason above — self-registers a `ContentProvider` inside the window being measured.

## Decision

- `Telemetry` is an interface with a mixed shape: strongly-typed methods for fixed-schema events (startup, jank, crash, ANR, memory, network) plus a generic `event()` escape hatch, `inline` extension functions, and an `isEnabled` check that guarantees zero allocation when a backend is disabled.
- Concrete implementations: `NoOpTelemetry`, `LogcatTelemetry`, `CompositeTelemetry` (fans out to multiple backends, isolates failures so one backend's exception doesn't take down the others), `SamplingTelemetry` (session-level sampling — decided once per session, not per event, and crash reports are never sampled out regardless of the sampling decision).
- The Firebase implementation lives in its own module, `:telemetry-firebase`, included in the build only when `gradle.properties`' `telemetryFirebaseEnabled=true` — at which point `settings.gradle.kts` includes the module, `:app` depends on it, and `:app` conditionally applies the `google-services`/`firebase-crashlytics` Gradle plugins via `buildscript classpath`, not via `plugins { apply false }` (the latter still resolves the plugin marker over the network even when unapplied; the conditional-classpath approach genuinely makes zero Firebase-related network requests when the flag is off).
- Collectors never self-register. `:app`'s `AppInitializers` explicitly drives startup order, split into `@Eager` (must run in `Application.onCreate`) and `@Deferred` (can wait until after first frame) groups — with the crash handler and cold-start timer as the sole exception, since those must be installed in `attachBaseContext`, before Hilt's graph even exists.
- `Set<Telemetry>` requires `@Multibinds` (see ADR 0004) so that "Firebase off, nothing else registered" is a valid, compiling empty set rather than a Hilt error.

## Consequences

- Forking the template and running `assembleDebug` with no Firebase project set up at all works out of the box — verified as an explicit acceptance check during the build-out.
- Turning Firebase on is exactly two file changes (flip the `gradle.properties` flag, drop in a real `google-services.json`) — documented in `TEMPLATE.md` and `telemetry-firebase/README.md`.
- The startup-ordering code in `AppInitializers` is now the one place that has to be updated whenever a new collector is added — there's no auto-discovery to fall back on. This mirrors the same trade-off ADR 0004 makes for DI wiring, for the same reason: an explicit, readable list beats implicit registration that's invisible until something goes wrong.
