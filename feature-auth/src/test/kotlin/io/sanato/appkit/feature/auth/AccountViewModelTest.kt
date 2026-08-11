package io.sanato.appkit.feature.auth

import app.cash.turbine.test
import io.sanato.appkit.core.auth.AuthProvider
import io.sanato.appkit.core.auth.AuthState
import io.sanato.appkit.core.auth.AuthUser
import io.sanato.appkit.core.auth.FakeAuthRepository
import io.sanato.appkit.core.auth.SignOutReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `reflects the signed-in user's profile`() =
        runTest {
            val user =
                AuthUser("uid", "a@b.com", "Ada", null, null, true, false, setOf(AuthProvider.Password), null, null)
            val repository = FakeAuthRepository(initial = AuthState.SignedIn(user))
            val viewModel = AccountViewModel(repository)

            viewModel.uiState.test {
                // `stateIn`'s own placeholder default (all-null `AccountUiState()`) is always
                // the very first emission to a new subscriber, regardless of what the upstream
                // `combine` resolves to — skip it and assert on the real, resolved value.
                skipItems(1)
                val state = awaitItem()
                assertEquals("a@b.com", state.email)
                assertEquals("Ada", state.displayName)
            }
        }

    @Test
    fun `signed-out state has no profile fields`() =
        runTest {
            val repository = FakeAuthRepository(initial = AuthState.SignedOut(SignOutReason.NeverSignedIn))
            val viewModel = AccountViewModel(repository)

            viewModel.uiState.test {
                // Here the placeholder default and the resolved value are identical (both
                // all-null), so there is nothing observable to skip past.
                val state = awaitItem()
                assertNull(state.email)
                assertNull(state.displayName)
            }
        }

    @Test
    fun `signOut calls through to the repository`() =
        runTest {
            val user =
                AuthUser("uid", "a@b.com", null, null, null, true, false, setOf(AuthProvider.Password), null, null)
            val repository = FakeAuthRepository(initial = AuthState.SignedIn(user))
            val viewModel = AccountViewModel(repository)

            viewModel.uiState.test {
                skipItems(1) // stateIn's placeholder default
                awaitItem() // resolved signed-in state
                viewModel.signOut()
                awaitItem() // signingOut = true
            }

            assertEquals(AuthState.SignedOut(SignOutReason.UserInitiated), repository.authState.value)
        }
}
