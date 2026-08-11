package io.sanato.appkit.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HashUtilsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `Sha256 hex of a known file matches the known digest`() {
        // Verified independently via `printf 'hello world' | shasum -a 256`.
        val file = tempFolder.newFile("hello.txt")
        file.writeText("hello world")

        assertEquals(
            "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
            Sha256.hex(file),
        )
    }

    @Test
    fun `Sha256 hex of bytes matches hex of an equivalent file`() {
        val bytes = "resume me please".toByteArray()
        val file = tempFolder.newFile("bytes.bin").apply { writeBytes(bytes) }

        assertEquals(Sha256.hex(bytes), Sha256.hex(file))
    }

    @Test
    fun `taskIdFor is deterministic for the same url and fileName`() {
        val first = taskIdFor("https://example.com/f.zip", "f.zip")
        val second = taskIdFor("https://example.com/f.zip", "f.zip")

        assertEquals(first, second)
        assertEquals(16, first.length)
    }

    @Test
    fun `taskIdFor differs when url or fileName differ`() {
        val base = taskIdFor("https://example.com/f.zip", "f.zip")

        assertNotEquals(base, taskIdFor("https://example.com/g.zip", "f.zip"))
        assertNotEquals(base, taskIdFor("https://example.com/f.zip", "g.zip"))
    }
}
