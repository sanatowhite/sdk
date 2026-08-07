# 0008 — Turn `core-*`/`feature-*` into real published SDK modules, not fork-and-copy

## Context

Before this decision, only `:updatechecker` was published as a real Maven/JitPack coordinate (see ADR 0001) — every other capability (`core-ui`, `core-net`, `core-data`, `core-telemetry`, the standard pages, startup orchestration) existed only as source inside this repository's `:app`. A fork got these by **copying the directory**, then editing the copy in place. That directly contradicted the goal stated when this rework started: a consumer should `implementation(...)` one coordinate and get working functionality, never touch or copy our source, and we publish + maintain versions on their behalf — the same experience as any real third-party Android library.

Three things stood in the way:

1. **Zero `api()` usage anywhere.** Every inter-module dependency was `implementation`, but `core-ui`/`core-net`/`core-data`'s public signatures are full of Compose/OkHttp/Retrofit/DataStore types. Publishing an AAR like this would hand a consumer a library whose public methods reference types they can't resolve — the classic "leaked implementation detail" bug, just at library-boundary scale.
2. **The functionality lived in the wrong place.** Standard pages, Hilt wiring, and startup orchestration were all in `:app`, which is explicitly **not** published (see below).
3. **JitPack's coordinate mechanics are non-negotiable.** JitPack derives the Maven `groupId` from the GitHub `<user>/<repo>` path — it is a URL route, not something a POM can override. A single-artifact repo gets `com.github.User:Repo`; the moment more than one Maven artifact exists in `~/.m2` after the build, JitPack switches to `com.github.User.Repo:Module` plus an auto-generated aggregate POM. Publishing 18 modules from one repo unavoidably falls into the second rule.

This also meant confronting the mechanism ADR 0001 built: mirroring `:updatechecker` out via `git subtree` to a separate single-module repository existed *only* to keep the old coordinate (`com.github.sanatowhite:version-check-sdk`) permanently frozen. Once the plan is "rename the repo and stop supporting the old coordinate," that mechanism has no object left to protect.

## Decision

### Repo rename + coordinate

- GitHub repository renamed `sanatowhite/version-check-sdk` → `sanatowhite/sdk` (a manual, one-time, externally-visible action — deliberately never automated).
- Coordinate form: `com.github.sanatowhite.sdk:<module>:<version>` for **every** publishable module, including `:updatechecker`, which now shares the same publish set, same tag, same version as everything else instead of being mirrored out separately. **ADR 0001 is superseded**: the subtree-mirror mechanism is gone; old tags `v1.0.0`/`v1.0.1`/`v1.0.2` are kept (JitPack caches builds permanently — deleting them only turns "resolves" into "404" for whoever's still pinned to them) but documented as deprecated, with no forward compatibility bridge to the new coordinate.
- `gradle/version.properties` gained `sdkGroup=com.github.sanatowhite.sdk` / `sdkVersion=1.0.0` as the single source of truth (same "checked-in file, not git-tag-derived" philosophy as ADR 0005's app versioning — the two axes are independent: app tags are `app-v*`, SDK tags are bare semver `*.*.*`).

### Module topology: four tiers

- **Tier 0** — `:updatechecker`, unchanged internally (zero deps, zero third-party beyond `core-ktx`/`coroutines-android`), now just joins the shared publish set instead of being mirrored.
- **Tier 1 (capability layer)** — `:core-common`, `:core-init`, `:core-ui`, `:core-net`, `:core-data`, `:core-telemetry`. Zero Hilt, `javax.inject` qualifiers only (ADR 0004's boundary, unchanged in spirit). Zero glue between each other except through `:core-common`.
- **Tier 2 (standard pages)** — `:feature-settings`, `:feature-feedback`, `:feature-licenses`, `:feature-update`. Each Screen (stateless, no Hilt required)/Route (wraps `hiltViewModel()`) split. Cross-feature coupling is zero — nullable callback parameters (`onNavigateToFeedback: (() -> Unit)? = null`, etc.) let a consumer depend on any subset.
- **Tier 3 (Hilt assembly)** — `core-common-hilt`, `core-init-hilt`, `core-data-hilt`, `core-telemetry-hilt`, `net-telemetry-hilt`, `telemetry-firebase`. The only place allowed to glue across Tier-1 boundaries (`net-telemetry-hilt` is the sole `core-net`↔`core-telemetry` bridge). Tier-2 feature modules apply Hilt directly themselves (their entire value proposition requires it), unlike Tier-1, which stays framework-agnostic.
- **`:sdk-bom`** — a `java-platform` module constraining all of the above to one version; consumers optionally `implementation(platform(...))` to stop writing per-module version numbers.

`verifyModuleGraph` (ADR 0003) was extended with a second hard rule beyond dependency direction: **a publishable module must not depend on a non-publishable one** — this is a publish-correctness check, not just a layering check, since violating it would put an unresolvable coordinate in a published POM.

### Why `-hilt` companion modules, not Hilt baked into the capability modules

Hilt's `@Module @InstallIn(...)` installs **unconditionally** the moment its declaring module is on the classpath and has itself run `hilt-compiler` — there is no per-binding opt-out, only per-**artifact** opt-out. A spike (`docs/adr/spike-0000-hilt-library-module-aggregation.md`) empirically confirmed the corollary that matters for publishing: a library module's `@Module` is only aggregated into a consumer's Dagger component if **that library module itself** applies the Hilt Gradle plugin and runs `hilt-compiler` — merely being present on the classpath is not enough, and the failure is **silent** (no compile error, just a missing binding) if the module comment claiming otherwise (found in `telemetry-firebase`, and factually wrong) is trusted. So: default wiring lives in a separate `-hilt` artifact: a consumer who wants zero Hilt lock-in depends on the plain Tier-1 module; a consumer who wants working DI out of the box adds the `-hilt` companion too; a consumer who wants a different binding (e.g. a different `UserSettingsRepository` implementation) `exclude()`s the companion module and writes one `@Binds` themselves (documented per-module in each `-hilt` module's README).

### Package migration

Published modules' root package moved `io.sanato.apptemplate.*` → `io.sanato.appkit.*`. Two reasons: it keeps `bootstrap.sh`'s rename script structurally safe (the fork rename's replace pattern is a three-segment token; `apptemplate`/`appkit` diverging at the third segment means even a wrong path-exclude glob could not accidentally rewrite a published module), and it reads consistently with the new `.sdk` coordinate branding. `:app`/`:benchmark`/`:baselineprofile` keep `io.sanato.apptemplate` — they are the non-published, fork-target half of this repository (see `CLAUDE.md`'s "what this repo is" section) and were never part of this migration.

### Publish scope: maximum, not minimum

All four tiers are published — capability layer, ready-to-use Hilt wiring, startup orchestration, and standard pages — so a consumer can freely mix and match rather than being forced to take an all-or-nothing bundle. This directly motivated the `-hilt` companion split above: publishing "maximum surface" while keeping Hilt optional are in tension unless wiring is its own artifact.

### Firebase as the app's default telemetry backend

Completes the note left pending in ADR 0006: `:app` now depends on `:telemetry-firebase` unconditionally (no `telemetryFirebaseEnabled` flag), ships a placeholder `app/google-services.json` (fake `project_id`, real per-variant `applicationId` entries) so `assembleDebug`/`assembleRelease` work out of the box, and forking developers swap in their own `google-services.json` — no code changes required. `:telemetry-firebase` itself still applies `sanato.android.hilt` (the Phase-0-spike fix) so its `FirebaseTelemetryModule` actually aggregates.

### The verification mechanism this all rests on

None of the above is trustworthy without a way to catch `api()`/`implementation` mistakes and Hilt-aggregation regressions in the **published artifacts**, not just in-repo `project()` dependencies (which Gradle substitutes transparently, masking exactly this class of bug). `checks/consumer-smoke/` is a deliberately separate Gradle build (its own `settings.gradle.kts`, never `include`d by the root build) that depends only on Maven coordinates — `mavenLocal()` locally, real JitPack remotely — and exercises every module's public surface plus a full Hilt component assembled from all five `-hilt` companions. See `checks/consumer-smoke/README.md` for what it checks and why independence from the root build is load-bearing, not incidental.

## Consequences

- Old coordinate consumers get a clean, documented break, not a silent one — `updatechecker/README.md` and the root `README.md` both state the old coordinate is frozen/deprecated with no migration shim.
- The `-hilt` companion split means roughly 2x the module count of "just Hilt everywhere," in exchange for zero DI lock-in on the capability layer — the explicit trade this repository has made consistently since ADR 0004.
- `verifyModuleGraph`/`verifySdkModuleList`/`verifySdkBomConstraints` are three separate machine-checked drift guards (module dependency direction, publish-set membership, BOM constraint list) because there are three separate hand-written lists (`settings.gradle.kts`'s `include(...)`, root `build.gradle.kts`'s `sdkModules`, `sdk-bom/build.gradle.kts`'s constraint list) that must move together — there is deliberately no single generated source of truth, since a build-time code-generation step here would itself need to be correct before any of these checks could trust it.
- `checks/consumer-smoke` is the only mechanism that would have caught the `com.google.dagger:hilt-android` explicit-dependency requirement discovered while building it (Hilt's Gradle plugin verifies the *directly-applying* module declares this itself; `-hilt` companion modules declaring it internally does not satisfy that check for a consumer's own app module) — a real, non-hypothetical example of the class of bug this verification layer exists to catch.
