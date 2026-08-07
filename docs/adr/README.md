# Architecture Decision Records

Lightweight ADRs (context / decision / consequences) for the choices made while turning `version-check-sdk` into `android-app-template`, and later while turning its `core-*`/`feature-*` modules into real published SDK artifacts. These record *why*, not *what* — the code and per-module READMEs already show what was built.

- [0001 — Monorepo template, SDK mirrored out via `git subtree`](0001-monorepo-with-subtree-sdk-mirror.md) — ⚠️ superseded by 0008
- [0002 — AGP 9 built-in Kotlin, with Kotlin/KSP versions overridden via `buildscript`](0002-agp9-built-in-kotlin-override.md)
- [0003 — Module topology, enforced by a real Gradle task, not just convention](0003-module-topology-and-verify-module-graph.md)
- [0004 — Hilt + KSP for `:app`, `javax.inject` only in `core-*`](0004-hilt-ksp-di-boundary.md) — ⚠️ partially superseded by 0008
- [0005 — `version.properties` as the single source of truth for app versioning](0005-version-properties-not-git-tags.md)
- [0006 — Pluggable `Telemetry` abstraction with Firebase as an optional, off-by-default backend](0006-telemetry-abstraction-optional-firebase.md) — ⚠️ partially superseded by 0008
- [0007 — What the template deliberately does not ship (Room, auth, in-app review)](0007-deliberately-excluded-capabilities.md)
- [0008 — Turn `core-*`/`feature-*` into real published SDK modules, not fork-and-copy](0008-sdk-publishing-architecture.md)
- [0009 — The `api()` vs `implementation()` judgment rule for published modules](0009-api-vs-implementation-judgment-rule.md)
- [0010 — `:logkit` is a log pipeline, not a detector; `:core-telemetry` still does the detecting](0010-logkit-pipeline-vs-apm-detection.md)
- [0011 — When a published module may carry a heavyweight vendor dependency](0011-vendor-backed-backend-modules.md)
- [spike 0000 — Hilt library-module `@Module` aggregation (empirical spike behind 0008)](spike-0000-hilt-library-module-aggregation.md)
