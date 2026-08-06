package io.sanato.updatechecker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets

class Sha256VerifierTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `matching sha256 returns true`() {
        val file = tempFolder.newFile("payload.bin")
        file.writeText("hello world", StandardCharsets.UTF_8)

        val matches =
            Sha256Verifier.matches(
                file,
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
            )

        assertTrue(matches)
    }

    @Test
    fun `mismatching sha256 returns false`() {
        val file = tempFolder.newFile("payload.bin")
        file.writeText("hello world", StandardCharsets.UTF_8)

        val matches = Sha256Verifier.matches(file, "0".repeat(64))

        assertFalse(matches)
    }

    @Test
    fun `case insensitive hash still matches`() {
        val file = tempFolder.newFile("payload.bin")
        file.writeText("hello world", StandardCharsets.UTF_8)

        val matches =
            Sha256Verifier.matches(
                file,
                "B94D27B9934D3E08A52E52D7DA7DABFAC484EFE37A5380EE9088F7ACE2EFCDE9",
            )

        assertTrue(matches)
    }
}
