# 0011 — When a published module may carry a heavyweight vendor dependency

## Context

Most published modules in this repository are held to a strict "zero third-party dependency, or the dependency never leaks past `implementation`" bar (see ADR 0009, and `:updatechecker`'s "four iron rules"). That bar is right for capability modules that have no inherent vendor lock-in — an HTTP client, a settings store, a telemetry abstraction all have multiple viable backends, so forcing every consumer to carry one specific vendor's SDK just to get the capability would be a bad trade.

Two modules break that pattern on purpose: `:telemetry-firebase` (Firebase Analytics/Crashlytics behind the `Telemetry` interface from `:core-telemetry`) and `:backupkit-drive` (Google Drive REST behind the `RemoteBackupStore` interface from `:backupkit`). Both exist specifically *because* the capability they provide has no vendor-neutral implementation worth writing generically — Firebase Crashlytics' crash-symbolication pipeline and Google Drive's OAuth/Authorization API are each tied to one vendor's actual infrastructure, not an interchangeable protocol. Refusing to publish these as separate modules wouldn't produce a more portable design; it would just mean every consumer who wants Firebase or Drive has to hand-roll their own copy of essentially the same wrapper this repo already wrote once, correctly, with tests.

Without a written rule, "can module X carry vendor dependency Y" turns into a case-by-case argument each time it comes up (a Dropbox module, a Mixpanel module, an AWS S3 module — the next one is only a matter of time). This ADR generalizes the two existing precedents into a checkable rule instead of relitigating it.

## Decision

A published module may depend on a heavyweight, vendor-specific SDK **only if all three hold**:

1. **The module exists for no other reason than this one vendor integration.** It implements exactly one interface already defined in a vendor-neutral module (`Telemetry` in `:telemetry-firebase`'s case, `RemoteBackupStore` in `:backupkit-drive`'s case) and contributes nothing else. A module that does several unrelated things and *also* happens to need a vendor SDK for one of them does not qualify — split the vendor-specific part out first.
2. **No vendor type ever appears in the module's own public signatures.** Not in a constructor parameter, not in a return type, not in a public property. `:backupkit-drive`'s `GmsDriveAuthorizer.authorize()` returns this module's own `DriveAuthResult`, never GMS's `AuthorizationResult`; `DriveTokenProvider`/`DriveBackupStore` mention nothing under `com.google.android.gms.*` anywhere a consumer's source code could be forced to reference it. A consumer who depends on the module never needs to write out the vendor's class names, even if they use every public method.
3. **The vendor dependency is `implementation`, never `api`.** It must not be transitively visible to a consumer's compile classpath at all. (This is orthogonal to whichever *internal* module the vendor-backed module itself depends on being `api` — `:backupkit-drive` correctly uses `api(project(":backupkit"))` because `DriveBackupStore implements RemoteBackupStore`, a type consumers do need to reference; that is ADR 0009's ordinary rule, not an exception this ADR grants.)
4. **A consumer who doesn't want the vendor never pays for it.** Not depending on the vendor-backed module must leave the vendor-neutral interface (`Telemetry`, `RemoteBackupStore`) fully usable with zero of that vendor's code anywhere in the dependency graph — checked concretely by the interface's other implementations (`SafBackupStore`, the empty `Set<Telemetry>` multibinding) compiling and working with the vendor-backed module absent entirely.

If a proposed module doesn't satisfy all four, it isn't a case for this exception — route it through the normal ADR 0009 `api`/`implementation` judgment instead, which will very likely reject it, and that rejection is correct: the dependency belongs to whichever capability module it's servicing, or it shouldn't be added at all.

## Consequences

- `:telemetry-firebase` and `:backupkit-drive` both document "为什么这个模块可以带三方依赖" in their own README, restating the three conditions above with citations to the specific code that satisfies each one — a reader shouldn't have to find this ADR to see the reasoning for the module they're looking at, only to see the general rule it's an instance of.
- `verifyModuleGraph` does not and cannot mechanically check condition 2 or 4 (it verifies the `project()` dependency graph, not public-signature contents or "what happens if this module is absent") — this remains a manual review point when a new vendor-backed module is proposed, the same way ADR 0009's per-line comments remain a manual audit trail rather than a fully mechanical check.
- The next candidate (a Dropbox `RemoteBackupStore`, a Mixpanel `Telemetry`, anything of that shape) should cite this ADR and walk through all four conditions explicitly in its own PR description, rather than pointing at the existing two modules as an unexamined precedent.
