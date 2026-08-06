package io.sanato.apptemplate.consent

import io.mockk.coVerify
import io.mockk.mockk
import io.sanato.apptemplate.core.data.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsentViewModelTest {
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
    fun `accept persists the current consent version and then invokes the callback`() =
        runTest {
            val repository = mockk<UserSettingsRepository>(relaxed = true)
            val viewModel = ConsentViewModel(repository)
            var callbackInvoked = false

            viewModel.accept { callbackInvoked = true }
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { repository.setConsentVersion(CURRENT_CONSENT_VERSION) }
            assertTrue(callbackInvoked)
        }
}
