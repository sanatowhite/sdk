package io.sanato.appkit.core.auth

/**
 * Registers a piece of per-user state that must be wiped on sign-out —
 * `@Singleton` repositories, in-memory caches, an open `WebSocketConnection`
 * — anything that would otherwise leak the previous user's data into the next
 * session. Mirrors the existing `Set<Telemetry>` / `Set<AppInitializer>`
 * multibinding pattern in this repo: implementations are collected via
 * `@IntoSet`, and [AuthRepository.signOut] must invoke every registered store
 * before flipping [AuthState] to `SignedOut`.
 */
fun interface SessionScopedStore {
    /** Must be idempotent and must never throw — a misbehaving store can't be allowed to block sign-out. */
    suspend fun clearForSignOut()
}
