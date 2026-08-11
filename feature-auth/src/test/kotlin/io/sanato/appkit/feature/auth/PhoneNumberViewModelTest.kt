package io.sanato.appkit.feature.auth

import android.app.Activity
import app.cash.turbine.test
import io.mockk.mockk
import io.sanato.appkit.core.auth.AuthError
import io.sanato.appkit.core.auth.AuthProvider
import io.sanato.appkit.core.auth.AuthUser
import io.sanato.appkit.core.auth.FakeAuthRepository
import io.sanato.appkit.core.auth.PhoneAuthEvent
import io.sanato.appkit.core.auth.PhoneVerificationId
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
class PhoneNumberViewModelTest {
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
    fun `submit is disabled without a leading plus sign`() =
        runTest {
            val viewModel = PhoneNumberViewModel(FakeAuthRepository())
            viewModel.onPhoneNumberChange("14155552671")
            assertFalse(viewModel.uiState.value.submitEnabled)

            viewModel.onPhoneNumberChange("+14155552671")
            assertTrue(viewModel.uiState.value.submitEnabled)
        }

    @Test
    fun `CodeSent emits the event with a masked phone number`() =
        runTest {
            val repository = FakeAuthRepository()
            repository.nextPhoneEvents = listOf(PhoneAuthEvent.CodeSent(PhoneVerificationId("verification-id")))
            val viewModel = PhoneNumberViewModel(repository)
            viewModel.onPhoneNumberChange("+14155552671")

            viewModel.events.test {
                viewModel.sendCode(mockk<Activity>(relaxed = true))
                testDispatcher.scheduler.advanceUntilIdle()
                val event = awaitItem() as PhoneNumberEvent.CodeSent
                assertEquals("verification-id", event.verificationId)
                assertFalse(event.phoneNumberMasked.contains("4155552"))
                assertTrue(event.phoneNumberMasked.endsWith("2671"))
            }
        }

    @Test
    fun `AutoRetrieved emits SignedIn directly, skipping the code screen`() =
        runTest {
            val user =
                AuthUser("uid", null, null, null, "+14155552671", false, false, setOf(AuthProvider.Phone), null, null)
            val repository = FakeAuthRepository()
            repository.nextPhoneEvents = listOf(PhoneAuthEvent.AutoRetrieved(user))
            val viewModel = PhoneNumberViewModel(repository)
            viewModel.onPhoneNumberChange("+14155552671")

            viewModel.events.test {
                viewModel.sendCode(mockk<Activity>(relaxed = true))
                testDispatcher.scheduler.advanceUntilIdle()
                assertEquals(PhoneNumberEvent.SignedIn, awaitItem())
            }
        }

    @Test
    fun `a failure surfaces the error`() =
        runTest {
            val repository = FakeAuthRepository()
            repository.nextPhoneEvents = listOf(PhoneAuthEvent.Failed(AuthError.InvalidPhoneNumber()))
            val viewModel = PhoneNumberViewModel(repository)
            // Locally well-shaped (passes this screen's own submitEnabled check) but the fake
            // repository is scripted to reject it anyway — this is testing the repository-failure
            // path, not the local-validation path (see the "submit is disabled" test for that).
            viewModel.onPhoneNumberChange("+15550000000")

            viewModel.sendCode(mockk<Activity>(relaxed = true))
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.error is AuthError.InvalidPhoneNumber)
        }

    @Test
    fun `maskPhoneNumber keeps the country code and last four digits`() {
        assertEquals("+•••••••2671", maskPhoneNumber("+14155552671"))
    }
}
