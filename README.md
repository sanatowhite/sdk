# android-app-template

A fork-able Android app template: Compose + Hilt + Navigation, in-app update checking, performance monitoring (startup/jank/crash/ANR/memory/network), and a standard set of pages (settings, about, consent, feedback) — with a CI release pipeline already wired up. Fork it, run one script, get a runnable app.

It also carries `:updatechecker`, a standalone in-app update checking library published independently to JitPack as `com.github.sanatowhite:version-check-sdk`. That module predates the template, has its own tag namespace (`v*` vs. the app's `app-v*`), and can be consumed on its own without any of the rest of this repo.

## I want the whole template

```bash
git clone <your-fork-url>
cd android-app-template
./scripts/bootstrap.sh com.yourcompany.yourapp "Your App Name"
```

This renames the `io.sanato.apptemplate` namespace to your own applicationId everywhere (packages, `namespace`/`applicationId`, manifest, proguard rules, baseline profile descriptors, docs), resets the version to `0.1.0`, renders your own `README.md`, runs a smoke test, and self-deletes. Review the resulting branch's diff before merging to `main`.

Then see:

- **`CLAUDE.md`** — commands, module graph, and the constraints future changes need to respect.
- **`TEMPLATE.md`** — post-fork checklist, a per-capability removal guide, a 15-minute recipe for adding Room, and what to expect from AGP 10.
- **`docs/adr/`** — why the build is shaped the way it is (AGP 9 built-in Kotlin, the JitPack coordinate-freeze mechanism, the module topology).

## I just want one capability

Every `core-*` module is independently deletable and has its own README describing what it does, what it explicitly excludes, and how to pull it in on its own.

| Module | What it is | How to get it |
|---|---|---|
| [`:updatechecker`](updatechecker/README.md) | In-app update check → download → SHA-256 verify → install. Zero third-party dependencies. | Already published: `implementation("com.github.sanatowhite:version-check-sdk:<tag>")` via JitPack — see its README for the full JitPack setup. |
| [`:core-common`](core-common/README.md) | Shared result/UI-state types and coroutine dispatcher qualifiers used by every other `core-*` module. | Not separately published — copy the module directory into your project. |
| [`:core-ui`](core-ui/README.md) | M3 theme (dynamic color), spacing tokens, loading/empty/error state components, page scaffolding. | Copy the module directory; only depends on `:core-common`. |
| [`:core-net`](core-net/README.md) | OkHttp/Retrofit setup, retry policy, `NetworkMonitor`, `safeApiCall` error wrapping. | Copy the module directory; only depends on `:core-common`. |
| [`:core-data`](core-data/README.md) | DataStore Preferences-backed user settings (theme, notifications, telemetry opt-in, consent version). | Copy the module directory; depends on `:core-common` and `:core-net` (shared error types only). |
| [`:core-telemetry`](core-telemetry/README.md) | Startup timing, jank (JankStats), crash/ANR capture, memory sampling, behind a pluggable `Telemetry` abstraction. | Copy the module directory; only depends on `:core-common`. Firebase backend is a separate, optional module. |
| [`:telemetry-firebase`](telemetry-firebase/README.md) | Firebase Analytics/Crashlytics implementation of `Telemetry`. Off by default. | Copy alongside `:core-telemetry`; needs your own `google-services.json`. |
| [`:debug-tools`](debug-tools/README.md) | In-app Debug Drawer (feature flag overrides, crash/ANR/OOM triggers, log viewer). `debugImplementation` only. | Copy the module directory; designed for this template's `:app` facade pattern, not a general-purpose library. |
| [`:benchmark`](benchmark/README.md) / [`:baselineprofile`](baselineprofile/README.md) | Macrobenchmark smoke tests + Baseline Profile generation for `:app`. | Not independently useful — these target `:app` specifically via `targetProjectPath`. |

None of these modules will resolve as a Maven/JitPack coordinate except `:updatechecker` — "copy the directory into your project" is the intended standalone consumption path for everything else, since publishing each one independently isn't worth the overhead for a template project. Each module's own dependency footprint (what it pulls in transitively) is documented in its README so you know what you're actually taking on.

## Repository layout

```
android-app-template/
├── app/                    -- the template shell; most of what you customize after forking
├── core-common/ core-ui/ core-net/ core-data/ core-telemetry/
├── debug-tools/            -- debugImplementation only, zero release residue
├── telemetry-firebase/     -- optional, off by default
├── updatechecker/          -- standalone SDK, independently published, own tag namespace
├── benchmark/ baselineprofile/
├── build-logic/convention/ -- precompiled Gradle script plugins
├── scripts/                -- bootstrap.sh, new-module.sh, dep-graph.sh
├── docs/adr/                -- architecture decision records
├── CLAUDE.md               -- guidance for AI coding assistants working in this repo
└── TEMPLATE.md             -- fork checklist and removal guide
```

## Building

```bash
./gradlew :app:assembleDebug            # the template app
./gradlew :updatechecker:test           # the SDK module, 19 tests, runs in isolation from everything else
./gradlew lintDebug spotlessCheck verifyModuleGraph   # required PR-check gates
```

JDK 17 required. See `CLAUDE.md` for the full command reference and `gradle/gradle-daemon-jvm.properties` if your local daemon can't find it.

## License

See individual module READMEs for any bundled third-party notices (e.g. AboutLibraries-generated attribution in `:app`).
