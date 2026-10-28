package io.sanato.appkit.safemode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeModeSettingsTest {
    @Test
    fun holdBelowMinimumIsRaisedToMinimum() {
        assertEquals(SafeModeSettings.MIN_HOLD_MS, SafeModeSettings.coerceHoldMs(100))
    }

    @Test
    fun holdAboveMaximumIsLoweredToMaximum() {
        assertEquals(SafeModeSettings.MAX_HOLD_MS, SafeModeSettings.coerceHoldMs(9_000))
    }

    @Test
    fun holdInsideRangeIsKept() {
        assertEquals(1_500L, SafeModeSettings.coerceHoldMs(1_500))
    }

    @Test
    fun optionsContainDefaultAndAreSortedWithinRange() {
        val options = SafeModeSettings.HOLD_OPTIONS_MS
        assertTrue(SafeModeSettings.DEFAULT_HOLD_MS in options)
        assertEquals(options.sorted(), options)
        assertTrue(options.all { it in SafeModeSettings.MIN_HOLD_MS..SafeModeSettings.MAX_HOLD_MS })
    }
}
