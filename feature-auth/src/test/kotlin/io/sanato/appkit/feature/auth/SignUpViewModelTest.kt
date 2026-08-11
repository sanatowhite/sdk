package io.sanato.appkit.feature.auth

import app.cash.turbine.test
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignUpViewModelTest {
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
    fun `submit is disabled until passwords match`() =
        runTest {
            val viewModel = SignUpViewModel(FakeAuthRepository())
            viewModel.onEmailChange("a@b.com")
            viewModel.onPasswordChange("hunter2")
            viewModel.onConfirmPasswordChange("hunter3")

            assertFalse(viewModel.uiState.value.submitEnabled)

            viewModel.onConfirmPasswordChange("hunter2")
            assertTrue(viewModel.uiState.value.submitEnabled)
        }

    @Test
    fun `a successful sign-up emits SignedUp`() =
        runTest {
            val repository = FakeAuthRepository()
            repository.nextResult = AppResult.Success(sampleUser())
            val viewModel = SignUpViewModel(repository)
            viewModel.onEmailChange("a@b.com")
            viewModel.onPasswordChange("hunter2")
            viewModel.onConfirmPasswordChange("hunter2")

            viewModel.events.test {
                viewModel.signUp()
                testDispatcher.scheduler.advanceUntilIdle()
                assertEquals(SignUpEvent.SignedUp, awaitItem())
            }
        }

    @Test
    fun `a failed sign-up surfaces the error`() =
        runTest {
            val repository = FakeAuthRepository()
            repository.nextResult = AppResult.Failure(AuthError.EmailAlreadyInUse(email = "a@b.com"))
            val viewModel = SignUpViewModel(repository)
            viewModel.onEmailChange("a@b.com")
            viewModel.onPasswordChange("hunter2")
            viewModel.onConfirmPasswordChange("hunter2")

            viewModel.signUp()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.error is AuthError.EmailAlreadyInUse)
            assertFalse(viewModel.uiState.value.inProgress)
        }
}
