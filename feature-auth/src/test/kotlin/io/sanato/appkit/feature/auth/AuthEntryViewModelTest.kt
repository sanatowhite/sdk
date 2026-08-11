package io.sanato.appkit.feature.auth

import io.sanato.appkit.core.auth.AuthProvider
import io.sanato.appkit.core.auth.AuthState
import io.sanato.appkit.core.auth.AuthUser
import io.sanato.appkit.core.auth.FakeAuthRepository
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
class AuthEntryViewModelTest {
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
    fun `resolves to signInRequired = false once the repository reports SignedIn`() =
        runTest {
            val user =
                AuthUser("uid", "a@b.com", null, null, null, true, false, setOf(AuthProvider.Password), null, null)
            val repository = FakeAuthRepository(initial = AuthState.SignedIn(user))

            val viewModel = AuthEntryViewModel(repository)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(false, viewModel.signInRequired.value)
        }

    @Test
    fun `resolves to signInRequired = true once the repository reports SignedOut`() =
        runTest {
            val repository = FakeAuthRepository(initial = AuthState.SignedOut())

            val viewModel = AuthEntryViewModel(repository)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(true, viewModel.signInRequired.value)
        }

    @Test
    fun `stays null while the repository has not resolved past Unknown`() =
        runTest {
            val repository = FakeAuthRepository(initial = AuthState.Unknown)

            val viewModel = AuthEntryViewModel(repository)
            // No time advanced past the splash timeout — deliberately no advanceUntilIdle() here.

            assertNull(viewModel.signInRequired.value)
        }
}
