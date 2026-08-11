# 0012 — Firebase-hosted auth + WebSocket-in-`:core-net`, supersedes 0007's "No authentication"

## Context

ADR 0007 explicitly rejected authentication: "Out of scope entirely — this is a
network-layer template, not an identity-and-session template... auth architecture
varies too much to templatize usefully." That rejection assumed a self-built backend,
where the variance really is large (OAuth vs. custom, token issuance, session storage,
refresh rotation). It did not anticipate a fully-hosted auth backend, where nearly all
of that variance collapses into "which vendor SDK do you call."

Two things changed the calculus:

1. The target consumer for this template is explicitly overseas-only with guaranteed
   GMS availability — no need to design around GMS-less devices or China-market
   distribution, which is what would have made a hosted-auth vendor a bad universal
   default.
2. `:core-net` already existed as a genuine OkHttp + Retrofit + kotlinx.serialization
   stack (`HttpClientFactory`, `RetryInterceptor`, `safeApiCall`/`AppError`,
   `NetworkMonitor`, `NetworkMetricsSink`) but had zero WebSocket support, and its
   `okHttpClient()` timeouts (`callTimeout(30s)`, `readTimeout(15s)`) are fatal to a
   long-lived connection. Any real login flow needs a place to plug a token into both
   HTTP and WebSocket transport, so the two gaps had to be closed together.

## Decision

### D1 — Firebase Auth, fully managed, not a self-built backend

Login backs onto `FirebaseAuth` directly (email+password, Google via Credential
Manager, Apple via `OAuthProvider`, phone via `PhoneAuthProvider`). No custom token
issuance, no self-hosted session store — `:core-auth`'s `AuthTokenProvider` hands out
Firebase ID tokens, and `FirebaseAuth`'s own refresh-token persistence is the only
persistence layer. This deliberately reopens the specific case ADR 0007 rejected
("auth architecture varies too much"): a fully-hosted vendor collapses that variance,
the same way `:telemetry-firebase` already made a hosted vendor the template's default
telemetry backend (ADR 0006/0008) despite `Telemetry` being provider-agnostic in
principle.

`:core-auth` (Tier 1, `AuthRepository`/`AuthUser`/`AuthError`/`AuthTokenProvider`,
zero vendor types) stays provider-agnostic on paper — a self-built-backend
implementation remains possible without touching `:feature-auth`'s UI — but the only
implementation this repo ships is `:auth-firebase`, following the ADR 0011
vendor-backed-module rule (`FirebaseAuth.getInstance()` looked up lazily, never in a
constructor; all Firebase/Credential-Manager/`googleid` deps `implementation`, never
`api`; zero vendor types cross `AuthRepository`'s public signatures — see
`auth-firebase/README.md`'s own "为什么这个模块可以带三方依赖" section for the
condition-by-condition walkthrough, including the `internal`-vs-`private` visibility
lesson: Kotlin `internal` top-level functions compile to plain `public` JVM bytecode,
so the javap-based `apiCheck` snapshot would otherwise have leaked `FirebaseUser`).

### D2 — WebSocket lives inside `:core-net`, not a separate `:core-ws` module

A standalone `:core-ws` would, under the Tier-1 "zero glue between siblings" rule,
have zero access to `AppError`/`NetworkMonitor`/`HttpClientFactory` and would have to
re-derive all of them. That's not a real boundary — a WebSocket handshake *is* an HTTP
Upgrade request, sharing the same connection pool, dispatcher, and TLS config as the
rest of `:core-net`'s HTTP stack. The only thing splitting it out would save a
WS-only consumer is the Retrofit + kotlinx-serialization coordinates, which doesn't
justify a fourteenth Tier-1 module and its associated gate-list entries. WebSocket
lives in a new `io.sanato.appkit.core.net.ws` subpackage instead, with its own sealed
`WebSocketError` hierarchy (not folded into `AppError`'s `sealed class` — adding a
case there is additive at the `apiCheck` javap-diff level but source-breaking for any
consumer's exhaustive non-`else` `when`, a break `apiCheck` cannot see) and a
`webSocketOkHttpClient()` factory that derives a new client from a shared base rather
than mutating `okHttpClient()`'s frozen signature.

### D3 — One bridge module (`:auth-net-hilt`), not two

The natural Tier-3 split would be one bridge for HTTP auth (`AuthInterceptor`/
`AuthTokenAuthenticator`) and a second for WebSocket auth (`WebSocketTokenProvider`
impl). Both would need exactly the same `allowedProjectDeps` entry
(`:core-auth` + `:core-net`), so splitting them buys no isolation — a consumer wiring
one almost always wants the other, since a logged-out user should lose both the
authenticated HTTP client and the long-lived socket at the same moment
(`SessionScopedStore`, driven by `:core-auth`'s multibinding, closes the socket and
blocks auto-reconnect on `signOut()`). `:auth-net-hilt` provides all three surfaces
from one module instead: it is the second cross-Tier-1 bridge after
`:net-telemetry-hilt` (see the module-graph comment in root `build.gradle.kts`).

### D4 — `:telemetry-firebase` is not a template for `:auth-firebase`

`:telemetry-firebase` was audited while designing `:auth-firebase` and found to
actually violate ADR 0011 conditions 2 and 3 — its `FirebaseTelemetry` constructor
signature exposes `FirebaseAnalytics`/`FirebaseCrashlytics` directly, and both are
brought in via `api()`. This is a pre-existing issue, left unfixed here (correcting it
would be a breaking API change to an already-published module, out of scope for this
PR), but `:auth-firebase` was written against `:backupkit-drive` — the one precedent
that actually satisfies all four ADR 0011 conditions — not against
`:telemetry-firebase`.

## Consequences

- ADR 0007's "No authentication" bullet is superseded by this ADR — see the
  annotation added there. The rest of ADR 0007 (no Room, no In-App Review, no
  WindowSizeClass, no semantic-release, no CODEOWNERS) is unaffected; only the
  authentication line was live enough to revisit.
- Four new published modules join `sdkModules`: `:core-auth` (Tier 1),
  `:auth-firebase` (Tier 3, vendor-backed per ADR 0011), `:auth-net-hilt` (Tier 3,
  second cross-Tier-1 bridge), `:feature-auth` (Tier 2). `:core-net` and
  `:net-telemetry-hilt` gain purely-additive WebSocket API surface; no new module was
  needed for either.
- A consumer who wants WebSocket without auth, or auth without WebSocket, still can:
  `:core-net`'s WebSocket API takes an optional `WebSocketTokenProvider` (default
  `null`, meaning "no auth"), and `:core-auth`/`:auth-firebase` have zero dependency on
  `:core-net` or `:auth-net-hilt`.
- `:app` declares its own `default_google_web_client_id` string resource rather than
  ever referencing the google-services-plugin-generated `R.string.default_web_client_id`
  directly — `:auth-firebase` looks it up by name at runtime
  (`resources.getIdentifier`), never by compile-time `R` reference. This matters
  because `R.string.default_web_client_id` is only generated when a Firebase Android
  app's `oauth_client` array is non-empty (Google sign-in enabled + a SHA fingerprint
  registered): a fork mid-setup, or a `google-services.json` with `oauth_client: []`,
  would fail to *compile* `:app` if any code referenced that generated resource
  directly, rather than degrading gracefully at runtime. The repo's own shipped
  `google-services.json` now points at a real project with Google sign-in already
  enabled (see TEMPLATE.md's "shared demo project" note), so `default_google_web_client_id`
  is non-empty by default — but the runtime-lookup design is what makes an empty value
  (a fresh fork before they've enabled Google, or a device without GMS) degrade to
  "Google unavailable" instead of a build break; both paths are covered by
  `AuthRepository.availableProviders()` and its tests.
