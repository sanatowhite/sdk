# 0001 — Monorepo template, SDK mirrored out via `git subtree`

## Context

`version-check-sdk` started as a single-module Android library published to JitPack as `com.github.sanatowhite:version-check-sdk` (JitPack derives that artifact coordinate from the git repository name). The goal was to turn this same repository into a fork-able app template while keeping the SDK publishable under its existing coordinate — existing consumers (the author's own 1-2 apps) must not have their dependency break.

JitPack has two coordinate-generation rules depending on how many Maven artifacts end up in `~/.m2` after the build: exactly one artifact → `com.github.<user>:<repo-name>`; more than one → `com.github.<user>.<repo-name>:<module>` plus an aggregate POM. Turning this repo into a multi-module monorepo (`:app`, `:core-*`, etc.) risks tripping the second rule the moment JitPack's root-level `publishToMavenLocal` + `tasks --all` touches more than one publishable module.

Renaming the GitHub repository itself (to `android-app-template`, which better describes what it now is) would also change the coordinate, since JitPack's artifact name **is** the repository name — not something declared in Gradle.

## Decision

1. Rename the GitHub repository to `android-app-template`.
2. Gate the JitPack build path (`JITPACK=true` env var, set in `jitpack.yml`) so `settings.gradle.kts` includes **only** `:updatechecker` in that mode — every other module is excluded from the JitPack build entirely, not just excluded from being published.
3. Mirror `:updatechecker` out to a dedicated, single-module repository (`version-check-sdk`) via a one-way `git subtree` push, so that repository's JitPack coordinate (`com.github.sanatowhite:version-check-sdk`) stays permanently frozen and decoupled from whatever the template repository is doing.
4. The `:updatechecker` module inside the monorepo remains the source of truth; the subtree-mirrored repo is a read-only publish target, never edited directly.

## Consequences

- Existing consumers' dependency coordinate never changes, regardless of what happens to the template repo's name, module count, or JitPack configuration.
- The mirror step is a manual, explicit, externally-visible action (creating a new public repository, pushing history) — deliberately **not** automated or run without a human confirming it, since it's hard to reverse cleanly once consumers start resolving against it.
- The `JITPACK` gate has to be re-verified on a throwaway branch (never a real tag, since JitPack tags are immutable) every time the module topology changes — this was done three times during the build-out (baseline, after the AGP 9 upgrade, after the full module topology landed).
