package io.sanato.appkit.safemode

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeModeFormatTest {
    @Test
    fun fractionalSecondsKeepOneDecimal() {
        assertEquals("1.2", SafeMode.formatSeconds(1_200))
        assertEquals("0.5", SafeMode.formatSeconds(500))
    }

    @Test
    fun wholeSecondsDropTrailingZero() {
        assertEquals("2", SafeMode.formatSeconds(2_000))
        assertEquals("3", SafeMode.formatSeconds(3_000))
    }
}
