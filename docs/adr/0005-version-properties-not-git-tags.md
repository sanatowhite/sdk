# 0005 — `version.properties` as the single source of truth for app versioning

## Context

Two obvious ways to determine an app build's `versionCode`/`versionName`: derive them from a git tag at build time, or read them from a checked-in file. AGP 9's variant API (`androidComponents.onVariants`) lets you observe and even override the version used for a variant's outputs — but setting a version there does **not** write back into `BuildConfig.VERSION_CODE`/`VERSION_NAME`, since those come from `defaultConfig` at configuration time, evaluated before `onVariants` runs. Deriving version purely through the variant API risks a real, silent inconsistency: the APK's manifest says one versionCode, `BuildConfig` compiled into the app says another.

Git-tag-derived versioning also complicates CI: GitHub Actions checkouts are shallow by default, and computing a version from tag history needs `fetch-depth: 0` plus tag-parsing logic that has to be duplicated between the release workflow and any local dev script that wants to know "what version am I building".

## Decision

`gradle/version.properties` (two lines: `versionCode`, `versionName`) is the single source of truth, read directly into `defaultConfig` in the `:app` convention plugin. `release.yml` asserts the tag being released (`app-v<versionName>`) matches this file before proceeding, rather than deriving the version from the tag. `GIT_SHA` (purely informational, shown on the About screen) is still fetched via a Gradle `ValueSource` at configuration time (compatible with configuration cache, unlike the removed `project.exec`), but it does not participate in version numbering.

`bootstrap.sh` resets this file to `versionCode=1` / `versionName=0.1.0` as part of forking, since a fresh fork has no meaningful version history to preserve.

This ADR covers app versioning only. `gradle/version.properties` also carries `sdkGroup`/`sdkVersion`, a separate, independent axis for the SDK modules published under ADR 0008 — same "checked-in file, not derived from a tag" philosophy, but its own tag namespace (bare semver `*.*.*`, vs. the app's `app-v*`) and its own assertion step in `sdk-release.yml`.

## Consequences

- No possible drift between the APK manifest's version and `BuildConfig`'s version — both trace back to the same `defaultConfig` read of the same file.
- CI does not need `fetch-depth: 0` for versioning purposes (it's still needed for `GIT_SHA`, which is a separate, non-blocking concern).
- Bumping the version is a manual, explicit one-line-file edit per release — there is no automatic "next version" derivation. This was a deliberate rejection of tools like `semantic-release`: the plan document's stated reasoning is that introducing automatic versioning would conflict with wanting to control exactly what gets tagged and released, not that automatic versioning is bad in general.
