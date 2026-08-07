# 0004 — Hilt + KSP for `:app`, `javax.inject` only in `core-*`

> ⚠️ **Superseded (partially).** "All actual `@Module`/`@Binds`/`@Provides` wiring
> lives in `:app/di/`" is no longer true — ADR 0008's SDK-publishing rework moved
> most of it into dedicated `-hilt` companion modules (`core-common-hilt`,
> `core-init-hilt`, `core-data-hilt`, `core-telemetry-hilt`, `net-telemetry-hilt`,
> plus `telemetry-firebase`), published as separate artifacts so a consumer can take
> a capability module's Hilt wiring or leave it. `:app/di/` still exists, but now only
> for genuinely app-specific bindings (the four `@Binds @IntoSet` initializer entries
> in `InitializerModule.kt`) — everything reusable moved out. The rest of this ADR
> (Hilt as the DI framework, `core-*` staying `javax.inject`-only, the `@Multibinds`
> requirement, the reasoning against per-module auto-registration) is still accurate.
> See ADR 0008 for why the wiring had to move (Hilt library-module aggregation
> requires the declaring module to run `hilt-compiler` itself — see
> `docs/adr/spike-0000-hilt-library-module-aggregation.md`).

## Context

The template needs dependency injection somewhere to wire together `:core-net`, `:core-data`, `:core-telemetry`, and the optional `:telemetry-firebase` backend into `:app`. Two questions: which DI framework, and where does it apply.

kapt is not compatible with AGP 9's built-in Kotlin model at all (see ADR 0002) — annotation processing has to go through KSP. Hilt's Gradle plugin only gained AGP 9.0 support starting at version 2.59; anything older fails outright.

A separate concern, independent of the AGP 9 migration: if every `core-*` module applied Hilt annotations directly on its public classes, lifting one of those modules into an unrelated project (the explicit "I just want one capability" use case this template is designed for) would drag Hilt along whether or not the destination project uses it — or uses a different DI framework, or none at all, or wires things by hand.

## Decision

- DI framework: Hilt 2.60.1 (safely above the 2.59 AGP-9-support floor) + KSP 2.3.11, applied only to `:app`.
- `core-*` modules use **constructor injection with plain `javax.inject.Inject`/`@Qualifier` annotations only** — no `@HiltAndroidApp`, `@AndroidEntryPoint`, `@Module`, or `@InstallIn` anywhere outside `:app`. Their public API takes its dependencies as constructor parameters; something else has to provide those parameters, but that something can be Hilt, Koin, manual wiring, or a test fake — the module doesn't care.
- All actual `@Module`/`@Binds`/`@Provides` wiring that assembles the graph lives in `:app/di/`, organized one file per `core-*` module it wires.
- `Set<Telemetry>` (see ADR 0006) requires an explicit `@Multibinds` declaration — Hilt treats an unbound empty multibinding set as a compile error, which is exactly the mechanism that makes "Firebase telemetry off, zero other backends registered" compile at all instead of crashing at runtime.

## Consequences

- KSP-based code generation is narrowed to a single module (`:app`), which also functions as risk containment for ADR 0002's compatibility concern, even though that wasn't the primary motivation.
- Anyone extracting a `core-*` module for standalone use gets a library with zero DI framework lock-in — verified by grepping each module's `src/main` for Hilt imports as part of writing its README's "already do / already don't" section.
- The cost is that `:app/di/` is the one place where all the wiring lives and has to be kept current as modules are added or removed — there's no per-module auto-registration to fall back on, which is a deliberate trade (see ADR 0006's rejection of `androidx.startup` for the same "no hidden auto-registration" reasoning).
