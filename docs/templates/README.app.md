# {{APP_NAME}}

Built from [android-app-template](https://github.com/sanatowhite/version-check-sdk) — applicationId `{{APP_ID}}`.

## What's included

- In-app update checking (`:updatechecker`) — check, download with progress, SHA-256 verify, install.
- Performance monitoring (`:core-telemetry`) — startup timing, jank, crashes, ANRs, memory, network, optional Firebase backend.
- Standard pages: settings (theme/dynamic color/language/notifications), about, licenses, privacy policy, terms of service, first-launch consent, What's New.
- Feedback page (screenshot + logs + device info -> email).
- Debug Drawer (debug builds only): feature flag overrides, crash/ANR/OOM triggers, startup/jank/network log viewer, and a LogKit panel (concurrency stress test, 5MB rotation, crash/ANR log durability, export & share).
- Encrypted rolling logs (`:logkit`): every crash/ANR/jank/network signal is written to disk, compressed and encrypted, capped at 5MB. Users can share the exported file for troubleshooting; only whoever holds the matching private key can read it. **Before you ship a real release, run `scripts/logkit-keygen.sh` and swap the built-in public key** — see `logkit/keys/README.md`.

## Getting started

```bash
./gradlew :app:assembleDebug
```

Replace the placeholder launcher icon (Android Studio > right-click `app/` > New > Image Asset), update `app/src/main/res/raw/{privacy_policy,terms_of_service}.md` with your real legal text, and point `app/src/main/kotlin/.../update/UpdateConfig.kt`'s `UPDATE_CONFIG_URL` at your own update JSON if you use the update-check feature.

## Signing

Debug/staging fall back to the debug key automatically. For a real release build, create `keystore.properties` at the repo root (never commit it):

```properties
store.file=/path/to/your.keystore
store.password=...
key.alias=...
key.password=...
```

## Removing what you don't need

Each `core-*` module can be deleted independently if you don't need that capability — see each module's own README for what depends on what. See `TEMPLATE.md` for the full removal guide.

## CI

`.github/workflows/pr-check.yml` runs on every push/PR to `main`. `release.yml` (tag `app-v*`) needs `RELEASE_KEYSTORE_BASE64`/`STORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` secrets configured before it can sign a real release.
