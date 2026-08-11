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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleUser() =
        AuthUser("uid", "a@b.com", null, null, null, true, false, setOf(AuthProvider.Password), null, null)

    @Test
    fun `fires on a genuine SignedIn to SignedOut transition`() =
        runTest {
            val repository = FakeAuthRepository(initial = AuthState.SignedIn(sampleUser()))
            val viewModel = AuthSessionViewModel(repository)

            viewModel.signedOutEvents.test {
                repository.setState(AuthState.SignedOut(SignOutReason.SessionExpired))
                assertEquals(SignOutReason.SessionExpired, awaitItem())
            }
        }

    @Test
    fun `does not fire for a cold-start Unknown to SignedOut transition`() =
        runTest {
            val repository = FakeAuthRepository(initial = AuthState.Unknown)
            val viewModel = AuthSessionViewModel(repository)

            viewModel.signedOutEvents.test {
                repository.setState(AuthState.SignedOut(SignOutReason.NeverSignedIn))
                expectNoEvents()
            }
        }

    @Test
    fun `does not fire when already signed out and staying signed out`() =
        runTest {
            val repository = FakeAuthRepository(initial = AuthState.SignedOut(SignOutReason.NeverSignedIn))
            val viewModel = AuthSessionViewModel(repository)

            viewModel.signedOutEvents.test {
                repository.setState(AuthState.SignedOut(SignOutReason.UserInitiated))
                expectNoEvents()
            }
        }
}
