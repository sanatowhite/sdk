package io.sanato.appkit.feature.auth

import io.sanato.appkit.core.auth.AuthError
import io.sanato.appkit.core.auth.FakeAuthRepository
import io.sanato.appkit.core.common.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {
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
    fun `a successful send flips emailSent`() =
        runTest {
            val repository = FakeAuthRepository()
            repository.nextUnitResult = AppResult.Success(Unit)
            val viewModel = ForgotPasswordViewModel(repository)
            viewModel.onEmailChange("a@b.com")

            viewModel.sendResetEmail()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.emailSent)
            assertFalse(viewModel.uiState.value.inProgress)
        }

    @Test
    fun `a failed send surfaces the error and does not flip emailSent`() =
        runTest {
            val repository = FakeAuthRepository()
            repository.nextUnitResult = AppResult.Failure(AuthError.InvalidEmail())
            val viewModel = ForgotPasswordViewModel(repository)
            viewModel.onEmailChange("not-an-email")

            viewModel.sendResetEmail()
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.emailSent)
            assertTrue(viewModel.uiState.value.error is AuthError.InvalidEmail)
        }

    @Test
    fun `submit is disabled when email is blank`() =
        runTest {
            val viewModel = ForgotPasswordViewModel(FakeAuthRepository())
            assertFalse(viewModel.uiState.value.submitEnabled)
        }
}
