package io.sanato.updatechecker

import java.io.File
import java.security.MessageDigest

object Sha256Verifier {
    fun matches(
        file: File,
        expectedHexSha256: String,
    ): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
        return actualHex.equals(expectedHexSha256.trim(), ignoreCase = true)
    }
}
