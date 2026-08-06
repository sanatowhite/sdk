# 0003 — Module topology, enforced by a real Gradle task, not just convention

## Context

A template's whole value proposition rests on modules being genuinely independent — someone should be able to delete `:core-telemetry` or lift `:core-net` into another project without discovering hidden coupling. Written conventions ("`:core-ui` shouldn't depend on `:core-net`") decay silently the moment someone adds an `implementation(project(...))` line under deadline pressure; nothing catches it until a much later, harder-to-diagnose failure (a template consumer's build breaking, or the JitPack SDK-only build silently including modules it should never see).

`:updatechecker` in particular has a harder constraint than "shouldn't depend on things" — it **must never** depend on any internal module, because the JitPack SDK-only build (see ADR 0001) excludes every other module from its `settings.gradle.kts` include list. A single accidental `implementation(project(":core-common"))` would make the SDK-only build fail outright, not just publish something slightly wrong.

## Decision

Define the allowed dependency graph as data (a `Map<String, Set<String>>` in the root `build.gradle.kts`) and check it with a real, always-runnable Gradle task, `verifyModuleGraph`, that introspects each module's actual `implementation`/`api` configurations at build time. The original (pre-ADR-0008) graph was six modules, all in one tier:

```
:app → :core-ui, :core-data, :core-net, :core-telemetry, :core-common, :updatechecker
:core-ui        → :core-common
:core-data      → :core-net, :core-common
:core-net       → :core-common
:core-telemetry → :core-common
:core-common    → (nothing)
:updatechecker  → (nothing)
```

ADR 0008's SDK-publishing rework grew this to 18 modules across four tiers, without changing the underlying mechanism — the map in `build.gradle.kts` is still the single source of truth, `verifyModuleGraph` still just introspects real `implementation`/`api` configurations. The current graph:

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

Tier 2 (standard pages):
  :feature-settings → :core-common, :core-ui, :core-data, :core-data-hilt, :core-common-hilt
  :feature-feedback → :core-common, :core-telemetry, :core-ui, :core-common-hilt
  :feature-licenses → :core-ui
  :feature-update   → :updatechecker, :core-ui

Tier 0 / other:
  :updatechecker → (nothing)   (hard rule — see ADR 0008)
  :debug-tools   → :core-telemetry
```

A second hard rule was added alongside the dependency-direction check: **a publishable module (one in the `sdkModules` list) must not depend on a non-publishable one.** This is a publish-correctness check, not a layering check — violating it would put a coordinate in a published POM that consumers can never resolve, a different (and worse) failure mode than a merely "wrong-tier" dependency.

The task fails the build with a listing of every violation if any module's actual project dependencies exceed what's declared allowed, or if a publishable module leaks a dependency on a non-publishable one. It is wired into the required `build-test-lint` CI job and `pr-check.yml`'s `sdk-guard` job, so a forbidden dependency edge fails the same PR check as a failing unit test — not a separate, easy-to-ignore lint warning.

This was deliberately built as plain Gradle API code rather than adopting a third-party module-boundary-enforcement plugin, to avoid one more moving dependency whose own compatibility with AGP 9's built-in Kotlin model would need separate verification.

## Consequences

- Verified to actually catch violations, not just pass by construction: a forbidden edge (`:core-ui` → `:core-net`) was temporarily added and confirmed to fail the task before being reverted, and the task passes clean on the real graph — re-verified again after the Tier-2/Tier-3 expansion (18 modules checked, zero skipped, in the current `verifyModuleGraph` output).
- `:benchmark`/`:baselineprofile` are exempted from the graph — they legitimately need to reference `:app` via `targetProjectPath`, which is a different (test-target) relationship than a build dependency, and enforcing the same rule there would be meaningless.
- This is enforcement, not documentation — the graph diagram in `CLAUDE.md` and this ADR must be kept in sync with the map in `build.gradle.kts` by hand; there's no single source both are generated from. If they drift, the *code* (the map) is authoritative.
- Three sibling drift-checks were added alongside `verifyModuleGraph` as the module count grew, each guarding a *different* hand-written list against the others: `verifySdkModuleList` (the `sdkModules` array vs. which subprojects actually apply `maven-publish`), `verifySdkBomConstraints` (`sdk-bom`'s constraint list vs. `sdkModules`), and `settings.gradle.kts`'s `include(...)` list itself (checked implicitly — a publishable module missing from `include(...)` shows up as a hard failure in `verifyModuleGraph`, not a silent skip). Four lists, three checks, deliberately no single generated source — see ADR 0008's consequences for why.
