package io.sanato.updatechecker

import org.junit.Assert.assertEquals
import org.junit.Test

class ApkDownloaderTest {
    @Test
    fun `target file name is derived from versionCode`() {
        val info =
            UpdateInfo(
                versionCode = 7,
                versionName = "1.0.1",
                apkUrl = "https://example.com/app.apk",
                sha256 = "abc",
                releaseNotes = "",
                force = false,
            )

        assertEquals("update-7.apk", ApkDownloader.targetFileName(info))
    }
}
