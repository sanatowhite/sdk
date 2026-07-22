package io.sanato.updatechecker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
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

    private lateinit var savedTimeZone: TimeZone

    @Before
    fun setUp() {
        // UpdateCheckPrefs.dateFormat() formats "yyyy-MM-dd" using the JVM default
        // timezone (correct for production: a daily throttle should follow the
        // device's local day). That makes the test's calendar-day math depend on
        // whichever timezone the test runs under, so pin it to UTC here to keep
        // the fixtures below deterministic across machines/CI runners.
        savedTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedTimeZone)
    }

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
