# 0003 — Module topology, enforced by a real Gradle task, not just convention

## Context

A template's whole value proposition rests on modules being genuinely independent — someone should be able to delete `:core-telemetry` or lift `:core-net` into another project without discovering hidden coupling. Written conventions ("`:core-ui` shouldn't depend on `:core-net`") decay silently the moment someone adds an `implementation(project(...))` line under deadline pressure; nothing catches it until a much later, harder-to-diagnose failure (a template consumer's build breaking, or the JitPack SDK-only build silently including modules it should never see).

`:updatechecker` in particular has a harder constraint than "shouldn't depend on things" — it **must never** depend on any internal module, because the JitPack SDK-only build (see ADR 0001) excludes every other module from its `settings.gradle.kts` include list. A single accidental `implementation(project(":core-common"))` would make the SDK-only build fail outright, not just publish something slightly wrong.

## Decision

Define the allowed dependency graph as data (a `Map<String, Set<String>>` in the root `build.gradle.kts`) and check it with a real, always-runnable Gradle task, `verifyModuleGraph`, that introspects each module's actual `implementation`/`api` configurations at build time:

```
:app → :core-ui, :core-data, :core-net, :core-telemetry, :core-common, :updatechecker
:core-ui        → :core-common
:core-data      → :core-net, :core-common
:core-net       → :core-common
:core-telemetry → :core-common
:core-common    → (nothing)
:updatechecker  → (nothing)
```

The task fails the build with a listing of every violation if any module's actual project dependencies exceed what's declared allowed. It is wired into the required `build-test-lint` CI job, so a forbidden dependency edge fails the same PR check as a failing unit test — not a separate, easy-to-ignore lint warning.

This was deliberately built as ~30 lines of plain Gradle API code rather than adopting a third-party module-boundary-enforcement plugin, to avoid one more moving dependency whose own compatibility with AGP 9's built-in Kotlin model would need separate verification.

## Consequences

- Verified to actually catch violations, not just pass by construction: a forbidden edge (`:core-ui` → `:core-net`) was temporarily added and confirmed to fail the task before being reverted, and the task passes clean on the real graph.
- `:benchmark`/`:baselineprofile` are exempted from the graph — they legitimately need to reference `:app` via `targetProjectPath`, which is a different (test-target) relationship than a build dependency, and enforcing the same rule there would be meaningless.
- This is enforcement, not documentation — the graph diagram in `CLAUDE.md` and the plan document must be kept in sync with the map in `build.gradle.kts` by hand; there's no single source both are generated from. If they drift, the *code* (the map) is authoritative.
