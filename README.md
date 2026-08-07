# android-app-template

A fork-able Android app template: Compose + Hilt + Navigation, in-app update checking, performance monitoring (startup/jank/crash/ANR/memory/network), and a standard set of pages (settings, about, consent, feedback) — with a CI release pipeline already wired up. Fork it, run one script, get a runnable app.

Every `core-*`/`feature-*` capability behind that app — plus `:updatechecker`, in-app update checking — is **also** published independently to JitPack as a real Maven coordinate under `com.github.sanatowhite.sdk:<module>:<version>`. `implementation(...)` one coordinate and get working functionality; never copy our source, never edit it in place. The app half and the SDK half share this one repo but publish on **different tag namespaces** (bare semver `*.*.*` for the SDK, `app-v*` for the app) and never depend on each other in either direction — see `docs/adr/0008-sdk-publishing-architecture.md` for the full reasoning.

> **Old coordinate deprecated.** `com.github.sanatowhite:version-check-sdk` (this repo's former name and `:updatechecker`'s former standalone coordinate) is frozen — the `v1.0.0`/`v1.0.1`/`v1.0.2` tags backing it have been deleted (a deliberate later call, made after weighing JitPack's permanent build cache against not wanting a stale coordinate hanging around indefinitely), so it no longer resolves for anyone not already using a cached copy. There is no migration bridge to the new coordinate; consumers on the old one need to switch explicitly.

## I want the whole template

```bash
git clone <your-fork-url>
cd android-app-template
./scripts/bootstrap.sh com.yourcompany.yourapp "Your App Name"
```

This renames the `io.sanato.apptemplate` namespace to your own applicationId everywhere (packages, `namespace`/`applicationId`, manifest, proguard rules, `google-services.json`, baseline profile descriptors, docs), resets the version to `0.1.0`, renders your own `README.md`, runs a smoke test, and self-deletes. Review the resulting branch's diff before merging to `main`.

Then see:

- **`CLAUDE.md`** — commands, module graph, and the constraints future changes need to respect.
- **`TEMPLATE.md`** — post-fork checklist, a per-capability removal guide, a 15-minute recipe for adding Room, and what to expect from AGP 10.
- **`docs/adr/`** — why the build is shaped the way it is (AGP 9 built-in Kotlin, the SDK-publishing architecture, the module topology, the `api()`/`implementation` judgment rule).

## I just want one (or a few) capabilities

Every module below is an independently versioned Maven coordinate — `implementation(...)` any subset you want, mix and match freely. Add the BOM to stop writing per-module version numbers:

```kotlin
dependencies {
    implementation(platform("com.github.sanatowhite.sdk:sdk-bom:1.0.0"))

    implementation("com.github.sanatowhite.sdk:core-ui")
    implementation("com.github.sanatowhite.sdk:core-data")
    implementation("com.github.sanatowhite.sdk:feature-settings")
    // ... any other module, no version number needed once the BOM is applied
}
```

| Module | What it is | Notes |
|---|---|---|
| [`:updatechecker`](updatechecker/README.md) | In-app update check → download → SHA-256 verify → install. Zero third-party dependencies beyond `core-ktx`/`coroutines-android`. | Zero internal dependencies — usable completely on its own. |
| [`:core-common`](core-common/README.md) | Shared result/UI-state types, coroutine dispatcher qualifiers, `AppBuildInfo`. | Depended on by almost everything else; depends on nothing. |
| [`:core-init`](core-init/README.md) | `AppInitializer`/`AppInitializers`/`FirstFrame` startup orchestration, framework-agnostic. | Pairs with `:core-init-hilt` for a ready-to-use `Application` base class. |
| [`:core-ui`](core-ui/README.md) | M3 theme (dynamic color), spacing tokens, loading/empty/error state components, page scaffolding. | Only depends on `:core-common`. No `apiCheck` (Compose compiler version drift is noise — see ADR 0009). |
| [`:core-net`](core-net/README.md) | OkHttp/Retrofit setup, retry policy, `NetworkMonitor`, `safeApiCall` error wrapping. | Only depends on `:core-common`. |
| [`:core-data`](core-data/README.md) | DataStore Preferences-backed user settings (theme, notifications, telemetry opt-in, consent version). | Interface-only (`UserSettingsRepository`); pair with `:core-data-hilt` for the default DataStore implementation. |
| [`:core-telemetry`](core-telemetry/README.md) | Startup timing, jank (JankStats), crash/ANR capture, memory sampling, behind a pluggable `Telemetry` abstraction. | Only depends on `:core-common`/`:core-init`. Firebase backend is a separate, optional module. |
| **`-hilt` companion modules** | [`core-common-hilt`](core-common-hilt/README.md), [`core-init-hilt`](core-init-hilt/README.md), [`core-data-hilt`](core-data-hilt/README.md), [`core-telemetry-hilt`](core-telemetry-hilt/README.md), [`net-telemetry-hilt`](net-telemetry-hilt/README.md) | Default Hilt wiring for the capability modules above, as **separate artifacts** — take a capability module without any DI framework lock-in, or add its `-hilt` companion for working Hilt bindings out of the box. See "DI boundary" in `CLAUDE.md` for why this split exists. |
| [`:telemetry-firebase`](telemetry-firebase/README.md) | Firebase Analytics/Crashlytics implementation of `Telemetry`. `:app`'s default backend. | Needs your own `google-services.json` to report anywhere real; compiles/runs fine with the placeholder one. |
| [`:debug-tools`](debug-tools/README.md) | In-app Debug Drawer (feature flag overrides, crash/ANR/OOM triggers, log viewer). `debugImplementation` only. | Depends on `:core-telemetry`. |
| [`:feature-settings`](feature-settings/README.md) | Settings/about/privacy-policy/terms-of-service/consent/What's New pages. Screen (stateless)/Route (Hilt) split. | Standalone import; cross-feature callbacks to `:feature-feedback`/`:feature-licenses`/`:feature-update` are all optional. |
| [`:feature-feedback`](feature-feedback/README.md) | Feedback page — local email compose with optional screenshot + log attachment. | Own `FileProvider` authority (`${applicationId}.feedback.fileprovider`), self-contained manifest entries. |
| [`:feature-licenses`](feature-licenses/README.md) | Open-source licenses page, backed by the AboutLibraries Gradle plugin's offline-generated data. | You apply the AboutLibraries plugin yourself; this module only renders. |
| [`:feature-update`](feature-update/README.md) | Update-check dialog + `UpdateCheckHost` one-line state holder, wired to `:updatechecker`. | Ships with a placeholder update-config URL; override via an optional Hilt binding. |
| [`:sdk-bom`](sdk-bom/README.md) | `java-platform` — version alignment for every module above. | No code, purely a version constraint list. |
| [`:benchmark`](benchmark/README.md) / [`:baselineprofile`](baselineprofile/README.md) | Macrobenchmark smoke tests + Baseline Profile generation for `:app`. | Not independently useful, not published — these target `:app` specifically via `targetProjectPath`. |

`checks/consumer-smoke/` (see its own README) is a separate, non-published Gradle project that exercises every module above against real published coordinates — read it if you want to see a complete, minimal example of consuming this SDK from outside the repo.

## Repository layout

```
android-app-template/
├── app/                    -- the template shell; most of what you customize after forking
├── core-common/ core-init/ core-ui/ core-net/ core-data/ core-telemetry/
├── core-common-hilt/ core-init-hilt/ core-data-hilt/ core-telemetry-hilt/ net-telemetry-hilt/
├── debug-tools/            -- debugImplementation only, zero release residue
├── telemetry-firebase/     -- :app's default telemetry backend
├── feature-settings/ feature-feedback/ feature-licenses/ feature-update/
├── sdk-bom/                -- java-platform version alignment
├── updatechecker/          -- standalone update-check SDK, zero internal dependencies
├── checks/consumer-smoke/  -- independent Gradle build verifying published coordinates actually work
├── benchmark/ baselineprofile/
├── build-logic/convention/ -- precompiled Gradle script plugins
├── scripts/                -- bootstrap.sh, new-module.sh, dep-graph.sh
├── docs/adr/                -- architecture decision records
├── CLAUDE.md               -- guidance for AI coding assistants working in this repo
└── TEMPLATE.md             -- fork checklist and removal guide
```

## Building

```bash
./gradlew :app:assembleDebug                          # the template app
./gradlew :updatechecker:test                         # the standalone SDK module, runs in isolation from everything else
./gradlew lintDebug spotlessCheck verifyModuleGraph   # required PR-check gates
JITPACK=true ./gradlew publishSdkToMavenLocal -Pversion=probe   # SDK-only build, mirrors what JitPack actually runs
```

JDK 17 required. See `CLAUDE.md` for the full command reference and `gradle/gradle-daemon-jvm.properties` if your local daemon can't find it.

## License

See individual module READMEs for any bundled third-party notices (e.g. AboutLibraries-generated attribution in `:app`).
