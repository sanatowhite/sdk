package io.sanato.updatechecker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test
    fun `remote higher than current returns true`() {
        assertTrue(VersionCompare.isNewerVersion(remoteVersionCode = 7, currentVersionCode = 6))
    }

    @Test
    fun `remote equal to current returns false`() {
        assertFalse(VersionCompare.isNewerVersion(remoteVersionCode = 6, currentVersionCode = 6))
    }

    @Test
    fun `remote lower than current returns false`() {
        assertFalse(VersionCompare.isNewerVersion(remoteVersionCode = 5, currentVersionCode = 6))
    }
}
