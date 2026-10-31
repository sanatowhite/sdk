package io.sanato.appkit.safemode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeModeSettingsTest {
    @Test
    fun holdBelowMinimumIsRaisedToMinimum() {
        assertEquals(SafeModeSettings.MIN_HOLD_MS, SafeModeSettings.coerceHoldMs(100))
        assertEquals(300L, SafeModeSettings.MIN_HOLD_MS)
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

    @Test
    fun holdOptionsIncludeShortChoices() {
        assertTrue(300L in SafeModeSettings.HOLD_OPTIONS_MS)
        assertTrue(500L in SafeModeSettings.HOLD_OPTIONS_MS)
    }

    @Test
    fun graceDefaultsToImmediateAndOptionsAreSortedWithinRange() {
        val options = SafeModeSettings.GRACE_OPTIONS_MS
        assertEquals(0L, SafeModeSettings.DEFAULT_GRACE_MS)
        assertEquals(0L, options.first())
        assertEquals(options.sorted(), options)
        assertTrue(options.all { it in 0L..SafeModeSettings.MAX_GRACE_MS })
    }

    @Test
    fun graceIsCoercedIntoRange() {
        assertEquals(0L, SafeModeSettings.coerceGraceMs(-1))
        assertEquals(SafeModeSettings.MAX_GRACE_MS, SafeModeSettings.coerceGraceMs(Long.MAX_VALUE))
        assertEquals(60_000L, SafeModeSettings.coerceGraceMs(60_000))
    }

    @Test
    fun graceLabelParts() {
        assertEquals(SafeModeSettings.GraceLabel.Immediately, SafeModeSettings.graceLabelParts(0))
        assertEquals(SafeModeSettings.GraceLabel.Seconds(30), SafeModeSettings.graceLabelParts(30_000))
        assertEquals(SafeModeSettings.GraceLabel.Minutes(1), SafeModeSettings.graceLabelParts(60_000))
        assertEquals(SafeModeSettings.GraceLabel.Minutes(30), SafeModeSettings.graceLabelParts(30 * 60_000L))
        assertEquals(SafeModeSettings.GraceLabel.Seconds(90), SafeModeSettings.graceLabelParts(90_000))
    }
}
