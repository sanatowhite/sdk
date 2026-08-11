# CLAUDE.md — `:downloadkit-hilt`

Directory-scoped guidance for Claude Code when working inside this module.
Repo-wide things (JDK version, spotless, the four gate commands) live in the
root `CLAUDE.md`, not here.

## What this module is

The default Hilt wiring for `:downloadkit`'s `Downloader` — one file
(`DownloadModule.kt`), no logic of its own. See `README.md`'s
"这是什么/不是什么".

## Dependency direction

`:downloadkit`, `:core-net`, `:core-common` — same three edges as
`:auth-net-hilt`'s shape (`api(:downloadkit)`, `implementation(:core-net)`,
`implementation(:core-common)` for `isDebuggableBuild()`). Don't add a fourth
without checking `allowedProjectDeps` in the root `build.gradle.kts` first.

## Testing

No test file, on purpose — matches `:net-telemetry-hilt`'s precedent for a
module that's pure declarative `@Provides`/`@BindsOptionalOf` wiring with zero
branching logic to unit-test. `Downloader` itself is tested in `:downloadkit`;
Hilt aggregation (the thing that actually breaks silently if this module's
plugin setup is wrong — see spike-0000) is verified by `checks/consumer-smoke`
against the real published Maven coordinate, not by a test in this module.

## The one command after touching this module

```bash
./gradlew :downloadkit-hilt:assembleDebug :downloadkit-hilt:apiCheck
./gradlew :app:hiltJavaCompileDebug   # confirms Hilt aggregation still resolves
```
