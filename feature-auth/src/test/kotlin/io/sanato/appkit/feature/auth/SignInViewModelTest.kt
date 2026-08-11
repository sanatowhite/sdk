package io.sanato.appkit.feature.auth

import android.app.Activity
import app.cash.turbine.test
import io.mockk.mockk
import io.sanato.appkit.core.auth.AuthError
import io.sanato.appkit.core.auth.AuthProvider
import io.sanato.appkit.core.auth.AuthUser
import io.sanato.appkit.core.auth.FakeAuthRepository
import io.sanato.appkit.core.common.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
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
        AuthUser(
            uid = "uid",
            email = "a@b.com",
            displayName = null,
            photoUrl = null,
            phoneNumber = null,
            isEmailVerified = true,
            isAnonymous = false,
            providers = setOf(AuthProvider.Password),
            createdAtMillis = null,
            lastSignInAtMillis = null,
        )

    @Test
    fun `loads available providers on init`() =
        runTest {
            val repository =
                FakeAuthRepository().apply { availableProviders = setOf(AuthProvider.Password, AuthProvider.Google) }
            val viewModel = SignInViewModel(repository)

            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                setOf(AuthProvider.Password, AuthProvider.Google),
                viewModel.uiState.value.availableProviders
                    .toSet(),
            )
        }

    @Test
    fun `a successful email sign-in emits SignedIn`() =
        runTest {
            val repository = FakeAuthRepository()
            repository.nextResult = AppResult.Success(sampleUser())
            val viewModel = SignInViewModel(repository)
            viewModel.onEmailChange("a@b.com")
            viewModel.onPasswordChange("hunter2")

            viewModel.events.test {
                viewModel.signInWithEmailPassword()
                testDispatcher.scheduler.advanceUntilIdle()
                assertEquals(SignInEvent.SignedIn, awaitItem())
            }
        }

    @Test
    fun `a failed email sign-in surfaces the error and clears inProgress`() =
        runTest {
            val repository = FakeAuthRepository()
            repository.nextResult = AppResult.Failure(AuthError.InvalidCredentials())
            val viewModel = SignInViewModel(repository)
            viewModel.onEmailChange("a@b.com")
            viewModel.onPasswordChange("wrong")

            viewModel.signInWithEmailPassword()
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.error is AuthError.InvalidCredentials)
            assertNull(state.inProgress)
        }

    @Test
    fun `cancelled provider sign-in is swallowed silently`() =
        runTest {
            val repository = FakeAuthRepository()
            repository.nextResult = AppResult.Failure(AuthError.Cancelled())
            val viewModel = SignInViewModel(repository)

            viewModel.signInWithProvider(AuthProvider.Google, activity = mockk<Activity>(relaxed = true))
            testDispatcher.scheduler.advanceUntilIdle()

            assertNull(viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.inProgress)
        }

    @Test
    fun `submit is disabled while empty and enabled once both fields are filled`() =
        runTest {
            val viewModel = SignInViewModel(FakeAuthRepository())
            assertTrue(!viewModel.uiState.value.submitEnabled)

            viewModel.onEmailChange("a@b.com")
            assertTrue(!viewModel.uiState.value.submitEnabled)

            viewModel.onPasswordChange("hunter2")
            assertTrue(viewModel.uiState.value.submitEnabled)
        }
}
