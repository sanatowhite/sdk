# CLAUDE.md — `:safemodekit`

Directory-scoped guidance for Claude Code when working inside this module.
Only things specific to this module — JDK version, spotless, the four gate
commands live in the root `CLAUDE.md`.

## What this module is, and its boundary

App disguise ("safe mode"): the host's launcher entry switches to a decoy
`activity-alias` that opens `DecoyActivity` (a WebView), and the real home
screen is only reachable through a pull-down-and-hold gesture plus
`BiometricPrompt` (weak biometric or device credential). The app relocks on
background (immediately, or after a configurable grace period that is checked
lazily on the next foreground) and on screen-off (configurable). The unlock
fires the moment the hold is satisfied, while the finger is still down. `SafeMode` is the only facade a consumer touches;
`SafeModeConfig` and `DecoyActivity` (referenced by full name from the host's
alias) are the only other public types. See `README.md` for the full boundary.

## Rules

1. **Zero internal module dependencies** (`verifyModuleGraph` maps it to
   `emptySet()`, see `docs/adr/0015-safemodekit.md`). AndroidX only.
2. **No convention plugin** beyond the two mix-ins (`published`, `api.check`) —
   same reason as `:backupkit`.
3. **Pure logic stays pure.** `HoldToUnlockDetector`, `SafeModeLockState`, and
   `SafeModeSettings` take time and values as parameters and have no Android
   imports; they carry all the unit tests. Put new decision logic there, not in
   `DecoyActivity`/`SafeModeGate`.
4. **Never put the alias names, home activity, or decoy branding in the SDK.**
   Those come from the host (`SafeModeConfig` + host manifest/resources).
5. **Additive-only public API.** `SafeMode`'s method set is snapshotted in
   `api/safemodekit.api`. The snapshot is javap-based, so `internal` classes
   show up in it too (same as `:backupkit`) — changing an internal class's
   signature still needs `apiDump`, but isn't a consumer-facing break.

## Behaviour that looks wrong but isn't

- `SafeModeLockState.allowExternalTrip()` is ignored while locked, and the
  allowance expires if the app doesn't actually go to the background within
  `TRIP_ARM_MS` (5s). Dialogs and the API 30+ `BiometricPrompt` don't trigger
  `onResume`, so without the arm window a stale allowance would let the next
  Home press skip locking.
- `SafeModeAuthenticator.authenticate()` marks authentication in progress
  (`beginAuthentication`/`endAuthentication`, which suppresses background
  locking but not screen-off locking) **only below API 30**: there,
  device-credential fallback launches the system confirm-credential activity,
  which stops the app, possibly long after the prompt opened — so the 5s trip
  window can't be used. On API 30+ the prompt is a SystemUI window; marking
  it there would let a Home press skip locking, because `onStop` can arrive
  before the cancel callback.
- Locking `finish()`es every non-decoy activity, which stops activity-bound
  background playback. This is intended and disclosed in the enable dialog.

## The one command after touching this module

```bash
./gradlew :safemodekit:testDebugUnitTest :safemodekit:apiCheck :safemodekit:lintDebug
```
