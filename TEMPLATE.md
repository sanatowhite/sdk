# TEMPLATE.md

This is a checklist and reference for people who forked `android-app-template` (or copied one of its modules). It answers three questions: what do I do right after forking, what do I delete if I don't need a capability, and what should I expect to break as Android tooling moves forward.

## Fork checklist

1. Run `./scripts/bootstrap.sh com.yourcompany.yourapp "Your App Name"` on a clean working tree. It renames the `io.sanato.apptemplate` namespace everywhere (never touching `:updatechecker` or `:logkit` — see `CLAUDE.md`'s "three-segment token" note), updates `rootProject.name`, resets `gradle/version.properties` to `1` / `0.1.0`, renders `README.md` from `docs/templates/README.app.md`, and self-deletes. Review the resulting branch's diff before merging — it runs a smoke test (`assembleDebug`, `:updatechecker:test`, `:logkit:test`, `verifyModuleGraph`) and refuses to commit if that fails.
2. Replace the launcher icon. `bootstrap.sh` deliberately does not touch binary image assets — open Android Studio, right-click `app/` → New → Image Asset, and replace `ic_launcher_*`.
3. Replace `app/src/main/res/raw/privacy_policy.md` and `terms_of_service.md` with your real legal text (they currently contain placeholder copy).
4. If you use the update-check feature, provide a `UpdateConfigOverride` Hilt binding (see `feature-update/README.md`) pointing at your own update JSON — without it, `:feature-update` stays on a placeholder URL and `checkForUpdate()` just returns `UpdateResult.Error` forever, it won't crash. JSON schema is documented in `updatechecker/README.md`; `sanatowhite/version_check` is a reference "static app store" repo you can fork alongside this one.
5. For a real release build, create `keystore.properties` at the repo root (never commit it — see `.gitignore`):
   ```properties
   store.file=/path/to/your.keystore
   store.password=...
   key.alias=...
   key.password=...
   ```
   Without it, `assembleRelease` falls back to the debug key with a build-time warning — fine for trying the template, not fine for a real release.
6. Firebase telemetry is on by default (`:app` ships with a placeholder `app/google-services.json` so it builds/runs without any setup). Replace that file with your own Firebase project's `google-services.json` (package name must match your new `applicationId`, or the Google Services plugin fails the build on purpose) — see `telemetry-firebase/README.md`. Don't want Firebase at all? Same README explains the two-line removal.
7. Configure CI secrets before your first tagged release: `RELEASE_KEYSTORE_BASE64` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` for `release.yml` (tag `app-v*`).
8. **If you use `:logkit`, run `./scripts/logkit-keygen.sh` and replace the built-in public key** in `logkit/src/main/java/io/sanato/logkit/BuiltInRecipientKey.kt`, then delete `logkit/keys/`. This is not optional the way most of this checklist is: the repo ships a THROWAWAY debug keypair (`logkit/keys/debug-private-key.pem`) so a fresh fork can decrypt its own dev-build logs immediately — but shipping a *release* build that still encrypts to that public key means every user's log file is readable by anyone who has the template's committed private key, i.e. anyone who has this repo. See `logkit/keys/README.md`.

## Removing what you don't need

Every `core-*`/`feature-*` module is independently deletable/swappable — they're published SDK coordinates now (`com.github.sanatowhite.sdk:<module>:<version>`), not code you copied into `:app`, so "removing" one is usually just dropping the `implementation(...)` line, not surgery on shared files. `verifyModuleGraph` will tell you immediately if you've left a dangling reference between modules that do stay. General procedure for removing a **published** module `:module-x` from a fork that consumes it via `project(...)` (the default, since `:app` builds against the same repo checkout — see `docs/adr/`):

1. Remove `implementation(project(":module-x"))` (or the Maven coordinate, once you've switched `:app` to consume published artifacts instead of `project(...)`) from `app/build.gradle.kts` and any Hilt module in `app/di/` that wires it up.
2. If you're not keeping the module around at all (not even as an unused dependency), also remove it from `settings.gradle.kts`'s `include(...)` list and delete the module directory.
3. Run `./gradlew :app:assembleDebug` — the compiler will point at every remaining reference.

Capability-specific notes:

| Remove | What goes with it | What to watch for |
|---|---|---|
| `:feature-update` (+ `:updatechecker`) | The update-check dialog, `UpdateCheckHost`/`UpdateViewModel`, and its Hilt module — all in `:feature-update`. `:updatechecker` itself has zero Compose/Hilt and zero internal dependents, so it's safe to drop on its own if you only want the raw checker API without the dialog wiring. | If you keep `:feature-settings`, its `settingsGraph(onCheckForUpdates = ...)` parameter just goes back to its default `null` — the "check for updates" row disappears from the settings page, no dead reference. |
| `:logkit` (+ `tools/logkit-decrypt`) | `app/src/main/kotlin/io/sanato/apptemplate/logging/` (`LogKitModule`/`LogKitDiagnosticSink`/`LogKitTelemetry`/`LogKitInstall`), the `LogKit.install`/`LogKit.i(...)` calls in `AppTemplateApp`/`MainActivity`/`AppNavHost`, `app/src/debug/.../LogKitDebugPanel.kt` (plugged into `:debug-tools`'s `DebugDrawer` via its `extraContent` slot — see `debug-tools/README.md`), `scripts/logkit-keygen.sh`. | `:core-telemetry`'s `DiagnosticLogSink` interface lives in `:core-telemetry` itself, defaulted to `DiagnosticLogSink.NoOp` (zero dependency edge to `:logkit`, mirrors the `NetworkMetricsSink` note above) — deleting `:logkit` does not require touching `:core-telemetry`, `:debug-tools`, `CrashRecorder`, or `AnrExitInfoReaper` at all. |
| `:core-telemetry` (+ `:telemetry-firebase`, `:core-telemetry-hilt`, `:net-telemetry-hilt`) | All startup/jank/crash/ANR/memory/network-timing collection, `AppInitializers`' `@Eager`/`@Deferred` driving code, the settings-page telemetry toggle, `:feature-feedback`'s log-attachment (its `RingLogBuffer` dependency). | `:core-net`'s `NetworkMetricsSink` interface lives in `:core-net` itself (zero dependency edge to telemetry) — deleting telemetry does not require touching `:core-net`. Dropping telemetry without dropping `:feature-feedback` means removing the "include logs" checkbox yourself (fork `:feature-feedback` or file an issue — it's not currently a config toggle). |
| `:debug-tools` | The Debug Drawer overlay and its `debugImplementation` wiring in `:app`. | Remove the `app/src/{debug,release,staging}/.../debug/DebugOverlay.kt` facade files too — they exist only to give the drawer a swap point per build type. |
| `:core-data` (+ `:core-data-hilt`) | DataStore-backed `UserSettings` (theme/dynamic color/notifications/telemetry-opt-in/consent version) and `:feature-settings`'s persistence. | `:feature-settings` depends on `:core-data`'s `UserSettingsRepository` interface as `api` — you'd need to fork or drop the settings screen entirely, or `exclude(module = "core-data-hilt")` and provide your own `@Binds ... : UserSettingsRepository` (see `core-data-hilt/README.md`). |
| `:core-net` | OkHttp/Retrofit setup, retry/timeout policy, `NetworkMonitor`, the remote feature-flag fetcher. | `:core-data` depends on it only for shared `AppResult`/`AppError` types, not for making requests — check that dependency is still meaningful before ripping both out together. |
| `:benchmark` / `:baselineprofile` | Macrobenchmark smoke tests and the baseline profile generator/consumer wiring in `:app` (the `baselineProfile {}` block, the `androidx.profileinstaller` dependency, `app/src/release/generated/baselineProfiles/`). | Purely additive to app performance — safe to delete with no functional impact, just lose the startup-time safety net. |
| `:feature-settings` | Settings/about/privacy-policy/terms-of-service/consent/What's New — `settingsGraph()` + `AppEntryViewModel` + `WhatsNewRoute` in `AppNavHost.kt`/`MainActivity.kt`. | Consent gating (`ConsentRoute` as conditional start destination) is wired through `AppEntryViewModel.consentRequired` in `MainActivity.kt` — removing the module without adjusting `MainActivity`'s start-destination logic leaves a dead reference, not a silent no-op. |
| `:feature-feedback` | The feedback page (`feedbackGraph()` in `AppNavHost.kt`) and `FeedbackScreenshotHost` wrapping `setContent`'s content in `MainActivity.kt`. | Its `${applicationId}.feedback.fileprovider` `<provider>`/`<queries>` declarations live in the module's own manifest (merged automatically) — nothing to clean up in `app/src/main/AndroidManifest.xml`. |
| `:feature-licenses` | The open-source-licenses page (`licensesGraph()` in `AppNavHost.kt`). | You can also drop the `com.mikepenz.aboutlibraries.plugin` application + `aboutLibraries {}` block from `app/build.gradle.kts` once nothing references `R.raw.aboutlibraries` anymore. |

## Adding Room (the module this template deliberately doesn't ship)

The template intentionally has no database — `:core-data` is DataStore Preferences only, sized for settings, not domain data. If you need real persistence:

1. Add a `room` version entry to `gradle/libs.versions.toml` (check the latest stable at the time — this template was built against a specific AGP/Kotlin combination, room's KSP integration moves independently).
2. Either extend `:core-data` (if the data genuinely is user settings/preferences) or create a new module (`:core-database` is a reasonable name) using the same `sanato.android.library` + `sanato.android.hilt` convention plugins `:core-data` already uses.
3. Room's Gradle plugin (`androidx.room`) needs to be applied alongside KSP for schema export — add `ksp(libs.room.compiler)` and set `room { schemaDirectory("$projectDir/schemas") }` so migrations are testable.
4. Follow `:core-data`'s existing pattern: repository interface + impl, `javax.inject` constructor injection (no Hilt annotations on the public API), a `testFixtures` in-memory fake for consumers to use in their own tests.
5. Wire the new module into `:app`'s Hilt graph the same way `:core-data`'s `DataModule` does.

This is a 15-minute addition once you've read `:core-data`'s existing code — the pattern is already there, Room just wasn't worth carrying as a default dependency for a template where a meaningful fraction of forks won't need a local database at all.

## What AGP 10 is expected to remove

Called out here because it's exactly the kind of thing that's invisible until a fork's Renovate PR fails: AGP 9's release notes already mark several things as deprecated-for-removal.

- **`com.android.legacy-kapt`** — kapt compatibility shim. This template already avoids it (Hilt runs on KSP), so nothing to do, but if you added a library that still requires kapt, expect it to stop working entirely on AGP 10, not just warn.
- **Old variant API remnants** (`applicationVariants`/`libraryVariants`/`variantFilter`) — already fully absent from this codebase (`androidComponents.onVariants()`/`beforeVariants()` are used instead in `build-logic`). If a Renovate-updated third-party plugin still calls the old API internally, that plugin — not this template — is what breaks.
- **`proguard-android.txt`** (non-optimize variant) — AGP 9 already disallows it via `android.r8.proguardAndroidTxt.disallowed=true`; this template only ever references `proguard-android-optimize.txt`. Don't add the non-optimize file back if you're debugging R8 issues — disable minification instead (`isMinifyEnabled = false` on a throwaway build type).
- **Non-KTS build scripts** — Groovy DSL support is expected to keep shrinking in convention-plugin-adjacent APIs (`CommonExtension` generics, `androidComponents`). This template is 100% Kotlin DSL already; if you add a module, keep it that way.

When in doubt: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` (wrapped as `scripts/dep-graph.sh`) shows you exactly what's resolving where before you upgrade anything.
