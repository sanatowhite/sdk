# 0015 — `:safemodekit`: app disguise as a zero-dependency `*kit` module

## Context

百川 (a consumer app) shipped a "safe mode": the launcher icon turns into a
decoy browser, opening it shows a web page, and the real UI is only reachable
via a hidden pull-down-and-hold gesture plus `BiometricPrompt`. Other apps
want the same capability, so it moves into this repo.

Three shape questions had to be answered:

1. Which tier / dependency shape?
2. Who declares the launcher `activity-alias`es?
3. Does the SDK ship settings UI?

## Decision

1. **A zero-internal-dependency `*kit` module, like `:backupkit`.** Nothing
   in it needs `:core-*` — it's lifecycle callbacks, `SharedPreferences`,
   `PackageManager.setComponentEnabledSetting`, a WebView, and
   `androidx.biometric`. Pure View, no Compose, no Hilt: the first consumer is
   a View/Dagger app, and a Compose host can still launch an Activity. Not
   splitting a `-hilt` companion: there is nothing to inject — `SafeMode` is
   process-global state by nature (one launcher, one lock).
2. **The host declares both aliases.** An `activity-alias` must point at the
   host's own home Activity, which a library manifest can't name. The decoy
   alias also carries the host's icon/label. The SDK only declares
   `DecoyActivity` (non-exported) and takes the alias class names through
   `SafeModeConfig`.
3. **No settings UI.** Every host's settings screen looks different (百川 uses
   QMUI list items). The SDK exposes `isEnabled/setEnabled`,
   `holdDurationMs/setHoldDurationMs`, the recommended
   `HOLD_DURATION_OPTIONS_MS`, `authenticate`, and a ready-made
   `enableExplanation` string; the README has a copy-pasteable example.

`androidx.biometric` is `api` because `FragmentActivity` is in
`SafeMode.authenticate`'s signature, and `androidx.appcompat` is `api` because
the public `DecoyActivity` extends `AppCompatActivity` (both ADR 0009;
consumer-smoke caught the second one). minSdk 24 like the rest of the
repo, which needs two compat paths below API 30 (device-credential detection
via `KeyguardManager`, and an external-trip allowance because the credential
fallback is a separate Activity there).

## Consequences

- `verifyModuleGraph` gains `":safemodekit" to emptySet<String>()`;
  `sdkModules`, `settings.gradle.kts`, and `sdk-bom` gain the module.
- Hosts must remove `LAUNCHER` from their home Activity and move it onto the
  real alias. On upgrade, some launchers drop the old icon once — documented
  in the README's FAQ.
- The "only one specific finger" request that started this feature is not
  possible on Android (`BiometricPrompt` doesn't identify the finger); the
  README states this so nobody re-asks.
