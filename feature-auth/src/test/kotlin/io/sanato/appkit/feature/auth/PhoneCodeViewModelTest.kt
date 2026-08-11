package io.sanato.appkit.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `@RunWith(AndroidJUnit4::class)`: `SavedStateHandle.toRoute<T>()` decodes
 * nav args through real `android.os.Bundle` machinery even in a plain unit
 * test — AGP's default stub jar throws "not mocked" for that, hence
 * Robolectric (same reasoning as `:auth-firebase`'s error-mapping tests).
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneCodeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun savedStateHandle() =
        SavedStateHandle(mapOf("verificationId" to "vid-123", "phoneNumberMasked" to "+1•••••••2671"))

    private fun sampleUser() =
        AuthUser("uid", null, null, null, "+14155552671", false, false, setOf(AuthProvider.Phone), null, null)

    @Test
    fun `initial state carries the masked phone number from the route args`() {
        val viewModel = PhoneCodeViewModel(FakeAuthRepository(), savedStateHandle())
        assertEquals("+1•••••••2671", viewModel.uiState.value.phoneNumberMasked)
    }

    @Test
    fun `entering a full-length code submits automatically`() =
        runTest {
            val repository = FakeAuthRepository()
            repository.nextResult = AppResult.Success(sampleUser())
            val viewModel = PhoneCodeViewModel(repository, savedStateHandle())

            viewModel.events.test {
                viewModel.onCodeChange("123456")
                testDispatcher.scheduler.advanceUntilIdle()
                assertEquals(PhoneCodeEvent.Verified, awaitItem())
            }
        }

    @Test
    fun `a wrong code clears the field and surfaces the error`() =
        runTest {
            val repository = FakeAuthRepository()
            repository.nextResult = AppResult.Failure(AuthError.InvalidVerificationCode())
            val viewModel = PhoneCodeViewModel(repository, savedStateHandle())

            viewModel.onCodeChange("000000")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("", viewModel.uiState.value.code)
            assertEquals(true, viewModel.uiState.value.error is AuthError.InvalidVerificationCode)
        }

    @Test
    fun `non-digit input is filtered out`() {
        val viewModel = PhoneCodeViewModel(FakeAuthRepository(), savedStateHandle())
        viewModel.onCodeChange("12a3b4")
        assertEquals("1234", viewModel.uiState.value.code)
    }
}
