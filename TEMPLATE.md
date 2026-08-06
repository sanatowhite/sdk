# TEMPLATE.md

This is a checklist and reference for people who forked `android-app-template` (or copied one of its modules). It answers three questions: what do I do right after forking, what do I delete if I don't need a capability, and what should I expect to break as Android tooling moves forward.

## Fork checklist

1. Run `./scripts/bootstrap.sh com.yourcompany.yourapp "Your App Name"` on a clean working tree. It renames the `io.sanato.apptemplate` namespace everywhere, updates `rootProject.name`, resets `gradle/version.properties` to `1` / `0.1.0`, renders `README.md` from `docs/templates/README.app.md`, and self-deletes. Review the resulting branch's diff before merging — it runs a smoke test (`assembleDebug`, `:updatechecker:test`, `verifyModuleGraph`) and refuses to commit if that fails.
2. Replace the launcher icon. `bootstrap.sh` deliberately does not touch binary image assets — open Android Studio, right-click `app/` → New → Image Asset, and replace `ic_launcher_*`.
3. Replace `app/src/main/res/raw/privacy_policy.md` and `terms_of_service.md` with your real legal text (they currently contain placeholder copy).
4. If you use the update-check feature, point `UpdateConfig.UPDATE_CONFIG_URL` at your own update JSON (see `updatechecker/README.md` for the JSON schema, and `sanatowhite/version_check` for a reference "static app store" repo you can fork alongside this one).
5. For a real release build, create `keystore.properties` at the repo root (never commit it — see `.gitignore`):
   ```properties
   store.file=/path/to/your.keystore
   store.password=...
   key.alias=...
   key.password=...
   ```
   Without it, `assembleRelease` falls back to the debug key with a build-time warning — fine for trying the template, not fine for a real release.
6. Decide whether you want Firebase telemetry. Default is off (`gradle.properties`: `telemetryFirebaseEnabled=false`). To turn it on: set that flag to `true` and drop a real `app/google-services.json` in place (package name must match your new `applicationId`, or the Google Services plugin fails the build on purpose). See `telemetry-firebase/README.md`.
7. Configure CI secrets before your first tagged release: `RELEASE_KEYSTORE_BASE64` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` for `release.yml` (tag `app-v*`).

## Removing what you don't need

Every `core-*` module is independently deletable — none of them depend on each other except through `core-common`, and `verifyModuleGraph` will tell you immediately if you've left a dangling reference. General procedure for removing module `:core-x`:

1. Remove it from `settings.gradle.kts`'s `include(...)` list.
2. Remove `implementation(project(":core-x"))` from `app/build.gradle.kts` and any Hilt module in `app/di/` that wires it up.
3. Delete the module directory.
4. Run `./gradlew :app:assembleDebug` — the compiler will point at every remaining reference.

Capability-specific notes:

| Remove | What goes with it | What to watch for |
|---|---|---|
| `:updatechecker` | The update-check dialog wiring in `:app` (`UpdateDialog.kt`, `UpdateViewModel`) and its Hilt module. | Nothing else depends on it — it has zero internal dependents by design. |
| `:core-telemetry` (+ `:telemetry-firebase`) | All startup/jank/crash/ANR/memory/network-timing collection, `AppInitializers`' `@Eager`/`@Deferred` driving code, the settings-page telemetry toggle. | `:core-net`'s `NetworkMetricsSink` interface lives in `:core-net` itself (zero dependency edge to telemetry) — deleting telemetry does not require touching `:core-net`. |
| `:debug-tools` | The Debug Drawer overlay and its `debugImplementation` wiring in `:app`. | Remove the `app/src/{debug,release,staging}/.../debug/DebugOverlay.kt` facade files too — they exist only to give the drawer a swap point per build type. |
| `:core-data` | DataStore-backed `UserSettings` (theme/dynamic color/notifications/telemetry-opt-in/consent version) and the settings page's persistence. | The settings **screen** itself lives in `:app` — you'd keep the UI and swap in your own persistence, or delete both together. |
| `:core-net` | OkHttp/Retrofit setup, retry/timeout policy, `NetworkMonitor`, the remote feature-flag fetcher. | `:core-data` depends on it only for shared `AppResult`/`AppError` types, not for making requests — check that dependency is still meaningful before ripping both out together. |
| `:benchmark` / `:baselineprofile` | Macrobenchmark smoke tests and the baseline profile generator/consumer wiring in `:app` (the `baselineProfile {}` block, the `androidx.profileinstaller` dependency, `app/src/release/generated/baselineProfiles/`). | Purely additive to app performance — safe to delete with no functional impact, just lose the startup-time safety net. |
| Standard pages (settings/about/consent/feedback/What's New) | Individual `.kt` files + `AppNavHost` routes in `:app`. Not module-scoped — these live directly in `:app/<feature>/`. | Consent gating (`ConsentRoute` as conditional start destination) is wired into `AppEntryViewModel` — removing consent without touching that leaves a dead route reference. |

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
