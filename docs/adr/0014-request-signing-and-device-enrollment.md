# 0014 — Device-bound request signing (`:core-security` / `:security-net-hilt`), design only

## Status

**Design accepted, implementation not started.** This ADR records the accepted
design so a future session (or another engineer) can pick up implementation
without re-deriving the reasoning below. No code has been written against it yet.

## Context

The only request-level auth today is `auth-net-hilt/AuthInterceptor`, which
attaches `Authorization: Bearer <Firebase idToken>`. That answers "who is the
caller" but leaves four things unaddressed: replay of a captured request,
scripts/curl calling the API directly once a token is extracted, parameter
tampering in transit, and plaintext sensitive bodies beyond TLS.

The originating idea was: sign requests with a timestamp + strong crypto, and
rely on a short (30s) server-side acceptance window — reasoning that "if the
crypto is strong enough, the attacker only has 30 seconds to break it."

### Why that premise is wrong

This is the load-bearing correction for the whole design, and it must survive
into any future re-read of this ADR:

- An attacker does not need to break the cryptography. If the signing key is
  embedded in the APK, Frida/IDA extracts it **once, offline**. After that the
  attacker computes a fresh, timestamp-valid signature for every request —
  the acceptance window imposes no constraint on him at all.
- **A time window defends against replaying an old message, not against
  forging a new one.** Forgery resistance comes entirely from whether the
  signing key is extractable — the window is orthogonal to that.
- A timestamp alone still permits replay: the same captured request can be
  fired many times inside one window unless paired with nonce de-duplication.
- The client's system clock is user-editable, so the timestamp must be
  calibrated against server time, not trusted as-is.

So the actual security ceiling is "can the Keystore-protected per-device key be
extracted", and the time window is only one component of replay defense, not
the source of forgery resistance.

### What this design does **not** defend against

Recorded here so it is never mistaken for a stronger guarantee later:

| Threat | Covered? |
|---|---|
| Replay | ✅ nonce de-dup + window |
| Parameter tampering | ✅ signature covers body hash + `Content-Type` |
| Sensitive body snooping | ✅ tiered — AES-256-GCM on an explicit sensitive-path allowlist |
| Extracted token reused on another device | ✅ private key never leaves Keystore |
| **Scripts/bots calling the API directly from a genuine device** | ❌ **not solvable client-side** |

The last row is the important one: an attacker on a **genuine, unmodified
device** can Frida-hook the signer function itself and get a local signing
oracle — no key extraction needed, no window to race. Obfuscation/whitebox
crypto raise the cost of that hook but do not close it; nothing client-side
can. The real mitigation for that threat is server-side: per-`deviceId` rate
limiting (this design's actual biggest practical win — IP-based limiting
becomes device-based, and `deviceId` cannot be forged without the enrollment
handshake), a cap on devices-per-account, behavioral risk scoring on
high-value endpoints, and eventually Play Integrity. This must ship
server-side in the same rollout, and is called out in the protocol doc's
"required server controls" section referenced below.

### Scope decided during brainstorming

| Axis | Decision |
|---|---|
| Key origin | Server-participated one-device-one-key via ECDH, private key in Android Keystore |
| Sign algorithm | HMAC-SHA256 (`SignAlg` enum reserves `ECDSA_P256`, not implemented this round) |
| Body encryption | Tiered — sign-only by default; AES-256-GCM only on an explicit sensitive-endpoint allowlist |
| Play Integrity | Interface only (`AttestationProvider`, no-op impl), no GMS dependency added |
| Server | Co-designed, but server code lives outside this repo |
| This round's deliverable | Client modules **not yet implemented** — this ADR + a not-yet-written `PROTOCOL.md` + golden vectors are the actual output of this round |

## Decision

### Module shape (planned, not yet scaffolded)

Mirrors the existing `:core-auth` / `:auth-net-hilt` pair exactly:

```
:core-security      → :core-common                                (Tier 1, zero Hilt)
:security-net-hilt  → :core-security, :core-net, :core-common      (Tier 3, cross-Tier-1 bridge)
```

Module count would go 27 → 29 once implemented.

### Four hard constraints inherited from existing code

These come from decisions already encoded in the repo and the design must obey
them, not work around them:

1. **`HttpClientFactory.okHttpClient()`'s signature is frozen.** Per
   `AuthNetModule.kt`'s own KDoc, adding a parameter (even defaulted) breaks
   source compatibility at existing named/positional call sites in a way
   `apiCheck`'s javap diff cannot see. `:security-net-hilt` must compose via
   `.newBuilder()` after the fact, exactly like `:auth-net-hilt` does —
   `:core-net` gets zero changes.
2. **Interceptors must never `runBlocking`.** `AuthInterceptor`'s KDoc states
   this explicitly; `AuthTokenAuthenticator`'s KDoc explains that OkHttp's
   `Authenticator` is the designated blocking extension point instead. So
   `SigningInterceptor` must be pure CPU (HMAC + header assembly only,
   never triggers enrollment), and a separate `SecurityAuthenticator` handles
   401 self-heal with the one deliberate `runBlocking` call, mirroring
   `AuthTokenAuthenticator`'s `priorResponse != null` single-retry guard.
3. **The `Authenticator` slot is exclusive and already occupied** by
   `AuthTokenAuthenticator` on the `@Authenticated` client. A
   `CompositeAuthenticator(delegates: List<Authenticator>)` — depending only
   on the OkHttp `Authenticator` interface, not on `:auth-net-hilt` (a
   Tier-3-to-Tier-3 dependency `verifyModuleGraph` would reject) — chains them,
   first non-null result wins. `:app/di/` is where the two get wired together,
   consistent with the DI-boundary section of the root `CLAUDE.md`.
4. **`RetryInterceptor` and nonce freshness depend on an implicit ordering.**
   `HttpClientFactory` adds `RetryInterceptor` before `additionalInterceptors`,
   so every retry re-flows through the signing layer and gets a fresh
   nonce/timestamp — currently safe, but silently breaks into `401 REPLAY` if
   that order is ever swapped, and only on the 5xx/429 retry path, which
   ordinary testing won't hit. Implementation must add both a code comment at
   that call site and a MockWebServer regression test asserting three retried
   requests carry three distinct `X-Sec-Nonce` values.

### Protocol summary

- Headers: `X-Sec-Version` / `X-Sec-Device-Id` / `X-Sec-Key-Id` / `X-Sec-Alg` /
  `X-Sec-Timestamp` / `X-Sec-Nonce` / `X-Sec-Signature`.
- Canonical string: version, method, path, sorted-and-encoded query, a fixed
  allowlist of signed headers (`content-type` only, to close the "client
  claims what it signed" downgrade surface SigV4's dynamic `SignedHeaders` has),
  body SHA-256 hex (or the literal `UNSIGNED-PAYLOAD` for one-shot/duplex
  bodies such as large uploads, restricted to an explicit server-side path
  allowlist), timestamp, nonce, device ID — newline-joined, byte-exact on both
  ends.
- Server validation order (cheap-to-expensive, to resist being used as a DoS
  vector): header/version check → ±60s clock window (not 30s — accommodates
  network latency, client clock drift, server NTP jitter; the actual replay
  defense is the next step) → nonce de-dup via Redis `SET NX EX 150` →
  key lookup → constant-time HMAC compare.
- Client clock calibration uses `SystemClock.elapsedRealtime()` plus an
  in-memory-only offset learned from a per-response `X-Server-Time` header —
  never `System.currentTimeMillis()` directly (user-editable) and never a
  persisted offset (would go stale silently across `elapsedRealtime` resets
  after process death).
- Enrollment forks on API level (`minSdk = 24`): API 31+ uses Keystore EC P-256
  `PURPOSE_AGREE_KEY` for on-device ECDH (shared secret never touches the
  network); API 24–30 falls back to Keystore RSA-2048-OAEP `PURPOSE_DECRYPT`
  (server-generated secret, RSA-wrapped in transit). Both derive identical
  `K_sign`/`K_enc` via HKDF-SHA256 afterward, so the fork is contained to one
  `EnrollmentCrypto` interface and does not affect the golden signing vectors.
- GCM IV management uses HiLo interval reservation (persist `counter + 4096`,
  advance in memory, never regress after process death) — naive per-encryption
  persistence writes are too slow and naive random 12-byte IVs risk reuse,
  which is catastrophic for GCM. `AndroidKeystoreDeviceKeyStore` is
  single-process only (`SharedPreferences` cross-process semantics are
  unreliable) and must assert the main process at init.
- Three independently-assembled OkHttp/WebSocket paths get different
  treatment: the business API client gets full signing; WebSocket gets
  handshake-only signing (no per-frame signing — the long-lived connection is
  already the replay boundary); `:downloadkit` gets **no signing at all** —
  Range-request semantics would need to join the signed-header allowlist and
  handle 206 responses, so downloads instead rely on server-issued presigned
  URLs (the standard S3-style pattern), keeping the authorization decision
  server-side.

## Consequences

- Two new SDK modules once implemented; the following hand-maintained lists
  need updating in lockstep (`verifyModuleGraph`/`verifySdkModuleList`/
  `verifySdkBomConstraints` catch drift mechanically, so this is not
  optional): `settings.gradle.kts` include list, `build.gradle.kts`'s
  `sdkModules` and `allowedProjectDeps` and `koverAggregatedModules`,
  `sdk-bom/build.gradle.kts`'s constraint list, and the module graph in the
  root `CLAUDE.md`.
- `core-net/HttpClientFactory.kt` gets exactly one comment added (documenting
  the interceptor-ordering invariant from constraint #4 above) — no signature
  or behavior change.
- The actual cross-team deliverable of implementing this ADR is not the code
  but `core-security/PROTOCOL.md` (canonical-string spec, validation order,
  error codes, Redis key/TTL format, required server-side controls, shadow-mode
  rollout order) plus a golden test-vector JSON that both client and server
  test suites consume — client/server work can run in parallel once those two
  documents exist, without either side waiting on the other's implementation.
- Explicitly deferred, not forgotten: real Play Integrity attestation
  (interface only for now), certificate pinning (orthogonal, separate ADR if
  pursued), `ECDSA_P256` per-request signing (enum reserved), per-frame
  WebSocket signing, and signing `:downloadkit` requests.
- A prior, more detailed working draft of this design (module file lists,
  per-milestone breakdown, full test plan) exists outside the repo in the
  planning session that produced this ADR; if implementation resumes, re-derive
  the file-level plan from the protocol summary above rather than assuming a
  plan file is still available, since planning-session artifacts are not
  persisted here.
