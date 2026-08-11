package io.sanato.appkit.core.auth

import io.sanato.appkit.core.common.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sanity coverage for the testFixtures fake itself — every ViewModel test in
 * `:feature-auth` depends on this behaving correctly, so it's worth pinning
 * down here rather than only ever exercising it indirectly.
 */
class FakeAuthRepositoryTest {
    @Test
    fun `initial state defaults to SignedOut`() =
        runTest {
            val repo = FakeAuthRepository()
            assertEquals(AuthState.SignedOut(SignOutReason.NeverSignedIn), repo.authState.value)
        }

    @Test
    fun `a successful sign-in flips authState to SignedIn`() =
        runTest {
            val repo = FakeAuthRepository()
            val user =
                AuthUser("uid", "a@b.com", null, null, null, true, false, setOf(AuthProvider.Password), null, null)
            repo.nextResult = AppResult.Success(user)

            val result = repo.signInWithEmail("a@b.com", "hunter2")

            assertEquals(AppResult.Success(user), result)
            assertEquals(AuthState.SignedIn(user), repo.authState.value)
        }

    @Test
    fun `a failed sign-in leaves authState untouched`() =
        runTest {
            val repo = FakeAuthRepository()
            repo.nextResult = AppResult.Failure(AuthError.InvalidCredentials())

            repo.signInWithEmail("a@b.com", "wrong")

            assertEquals(AuthState.SignedOut(SignOutReason.NeverSignedIn), repo.authState.value)
        }

    @Test
    fun `signOut always succeeds and reports UserInitiated`() =
        runTest {
            val repo = FakeAuthRepository(initial = AuthState.SignedIn(sampleUser()))

            val result = repo.signOut()

            assertTrue(result is AppResult.Success)
            assertEquals(AuthState.SignedOut(SignOutReason.UserInitiated), repo.authState.value)
        }

    @Test
    fun `cachedIdToken and currentIdToken read the same backing field`() =
        runTest {
            val repo = FakeAuthRepository()
            repo.token = "abc"

            assertEquals("abc", repo.cachedIdToken())
            assertEquals("abc", repo.currentIdToken())

            repo.invalidateToken()

            assertEquals(null, repo.cachedIdToken())
        }

    private fun sampleUser() =
        AuthUser("uid", "a@b.com", null, null, null, true, false, setOf(AuthProvider.Password), null, null)
}
