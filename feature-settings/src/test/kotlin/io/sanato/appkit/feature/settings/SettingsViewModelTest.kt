package io.sanato.appkit.feature.settings

import app.cash.turbine.test
import io.sanato.appkit.core.data.FakeUserSettingsRepository
import io.sanato.appkit.core.data.ThemeMode
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
class SettingsViewModelTest {
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
    fun `setThemeMode updates uiState`() =
        runTest {
            val repository = FakeUserSettingsRepository()
            val viewModel = SettingsViewModel(repository)

            viewModel.uiState.test {
                assertEquals(ThemeMode.SYSTEM, awaitItem().themeMode)

                viewModel.setThemeMode(ThemeMode.DARK)

                assertEquals(ThemeMode.DARK, awaitItem().themeMode)
            }
        }

    @Test
    fun `setTelemetryEnabled updates uiState`() =
        runTest {
            val repository = FakeUserSettingsRepository()
            val viewModel = SettingsViewModel(repository)

            viewModel.uiState.test {
                assertEquals(true, awaitItem().telemetryEnabled)

                viewModel.setTelemetryEnabled(false)

                assertEquals(false, awaitItem().telemetryEnabled)
            }
        }
}
