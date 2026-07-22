package io.sanato.updatechecker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateConfigParserTest {
    @Test
    fun `parses a well formed config`() {
        val json = """
            {
              "versionCode": 7,
              "versionName": "1.0.1",
              "apkUrl": "https://raw.githubusercontent.com/sanatowhite/version_check/main/sanato-diary-v1.0.1-code7-release.apk",
              "sha256": "10438c45fd4d2d2e4752025ed3a65c3fbfcca0e507c35787e939bb05d4ac1a3d",
              "releaseNotes": "fix things",
              "force": false
            }
        """.trimIndent()

        val info = UpdateConfigParser.parse(json)

        assertEquals(7L, info.versionCode)
        assertEquals("1.0.1", info.versionName)
        assertEquals("fix things", info.releaseNotes)
        assertFalse(info.force)
    }

    @Test
    fun `releaseNotes defaults to empty string when absent`() {
        val json = """
            {
              "versionCode": 7,
              "versionName": "1.0.1",
              "apkUrl": "https://example.com/app.apk",
              "sha256": "abc",
              "force": true
            }
        """.trimIndent()

        val info = UpdateConfigParser.parse(json)

        assertEquals("", info.releaseNotes)
        assertEquals(true, info.force)
    }

    @Test
    fun `throws on missing required field`() {
        val json = """{"versionCode": 7}"""

        assertThrows(UpdateConfigParser.MalformedConfigException::class.java) {
            UpdateConfigParser.parse(json)
        }
    }

    @Test
    fun `throws on invalid json`() {
        assertThrows(UpdateConfigParser.MalformedConfigException::class.java) {
            UpdateConfigParser.parse("not json")
        }
    }
}
