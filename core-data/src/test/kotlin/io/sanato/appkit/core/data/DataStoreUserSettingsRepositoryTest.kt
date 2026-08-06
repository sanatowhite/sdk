package io.sanato.appkit.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreUserSettingsRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun repository(): DataStoreUserSettingsRepository {
        val dataStore =
            PreferenceDataStoreFactory.create(
                produceFile = { tempFolder.newFile("test_${System.nanoTime()}.preferences_pb") },
            )
        return DataStoreUserSettingsRepository(dataStore)
    }

    @Test
    fun `defaults are used when nothing written`() =
        runTest {
            assertEquals(UserSettings(), repository().settings.first())
        }

    @Test
    fun `setThemeMode persists and reads back`() =
        runTest {
            val repo = repository()
            repo.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, repo.settings.first().themeMode)
        }

    @Test
    fun `setTelemetryEnabled persists and reads back`() =
        runTest {
            val repo = repository()
            repo.setTelemetryEnabled(false)
            assertEquals(false, repo.settings.first().telemetryEnabled)
        }

    @Test
    fun `setConsentVersion persists and reads back`() =
        runTest {
            val repo = repository()
            repo.setConsentVersion(3)
            assertEquals(3, repo.settings.first().consentVersion)
        }
}
