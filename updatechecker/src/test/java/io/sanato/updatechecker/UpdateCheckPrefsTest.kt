package io.sanato.updatechecker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateCheckPrefsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val day1 = 1_700_000_000_000L
    private val sameDayLater = day1 + 3_600_000L
    private val nextDay = day1 + 24L * 3_600_000L

    @Test
    fun `first run always allows auto check`() {
        assertTrue(UpdateCheckPrefs.shouldAutoCheck(context, day1))
    }

    @Test
    fun `same day after marking checked does not allow auto check again`() {
        UpdateCheckPrefs.markChecked(context, day1)
        assertFalse(UpdateCheckPrefs.shouldAutoCheck(context, sameDayLater))
    }

    @Test
    fun `next day after marking checked allows auto check again`() {
        UpdateCheckPrefs.markChecked(context, day1)
        assertTrue(UpdateCheckPrefs.shouldAutoCheck(context, nextDay))
    }
}
