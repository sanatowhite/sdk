package io.sanato.appkit.backup.format

import io.sanato.appkit.backup.core.PassphraseProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * [Sbk1BackupCodec.unseal] 按 magic 自动分派到 legacy 解码器——这是"SDK 只写新格式，
 * 但必须能读 legacy"这条核心诉求的入口验收：调用方不需要关心一个 `.sdb` 文件到底是
 * 新格式还是三种历史格式之一，`unseal()` 统一处理。
 */
class Sbk1LegacyDispatchTest {
    private lateinit var tmpDir: File
    private val codec = Sbk1BackupCodec()
    private val passphrase = PassphraseProvider { LEGACY_SECRET }

    @Before
    fun setUp() {
        tmpDir =
            File.createTempFile("dispatchtest", "").apply {
                delete()
                mkdirs()
            }
    }

    @Test
    fun unseal_dispatchesToLegacySdb1() =
        runTest {
            val content = "legacy sdb1 content"
            val sealed = File(tmpDir, "v1.sdb").apply { sealV1(content, LEGACY_SECRET, this) }
            val output = File(tmpDir, "out.bin")

            codec.unseal(sealed, output, listOf(passphrase))

            assertArrayEquals(content.toByteArray(), output.readBytes())
        }

    @Test
    fun unseal_dispatchesToLegacySdb2() =
        runTest {
            val content = "legacy sdb2 content"
            val sealed = File(tmpDir, "v2.sdb").apply { sealV2(content, LEGACY_SECRET, this) }
            val output = File(tmpDir, "out.bin")

            codec.unseal(sealed, output, listOf(passphrase))

            assertArrayEquals(content.toByteArray(), output.readBytes())
        }

    @Test
    fun unseal_plainZipPassesThroughUnmodified() =
        runTest {
            val zipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4, 5)
            val plainZip = File(tmpDir, "plain.zip").apply { writeBytes(zipBytes) }
            val output = File(tmpDir, "out.zip")

            codec.unseal(plainZip, output, listOf(passphrase))

            assertArrayEquals(zipBytes, output.readBytes())
        }

    @Test
    fun inspect_reportsLegacyFormatTrue() {
        val sealed = File(tmpDir, "v2.sdb").apply { sealV2("x", LEGACY_SECRET, this) }
        val info = codec.inspect(sealed)
        assertTrue(info.isLegacyFormat)
    }

    private fun deriveAesKey(
        passphrase: ByteArray,
        salt: ByteArray,
    ): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.map { it.toInt().toChar() }.toCharArray(), salt, 120_000, 256)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun deriveHmacKey(
        passphrase: ByteArray,
        salt: ByteArray,
    ): SecretKeySpec {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(passphrase)
        md.update(salt)
        md.update(byteArrayOf(0x48, 0x4D))
        return SecretKeySpec(md.digest(), "HmacSHA256")
    }

    private fun sealV1(
        content: String,
        passphrase: ByteArray,
        output: File,
    ) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveAesKey(passphrase, salt), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(content.toByteArray())
        val payload = salt + iv + ciphertext
        val hmac = Mac.getInstance("HmacSHA256").apply { init(deriveHmacKey(passphrase, salt)) }.doFinal(payload)
        output.writeBytes("SDB1".toByteArray(Charsets.US_ASCII) + hmac + payload)
    }

    private fun sealV2(
        content: String,
        passphrase: ByteArray,
        output: File,
    ) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveAesKey(passphrase, salt), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(content.toByteArray())
        val payload = salt + iv + ciphertext
        val hmac = Mac.getInstance("HmacSHA256").apply { init(deriveHmacKey(passphrase, salt)) }.doFinal(payload)
        output.writeBytes("SDB2".toByteArray(Charsets.US_ASCII) + hmac + payload)
    }

    private companion object {
        val LEGACY_SECRET = "legacy-secret".toByteArray()
    }
}
