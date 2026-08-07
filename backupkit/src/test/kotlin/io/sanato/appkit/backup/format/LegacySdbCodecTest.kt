package io.sanato.appkit.backup.format

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
 * [LegacySdbCodec] 是只读的，这里的编码器只为造测试夹具存在——逐字节复刻迁移前旧 app
 * 的 seal 实现（PBKDF2 120k 轮 + AES/GCM 或 AES/CTR+HMAC），验证解码器能正确解开
 * 用同一套算法产出的真实历史格式文件。
 */
class LegacySdbCodecTest {
    private lateinit var tmpDir: File
    private val passphrase = "legacy-portable-key".toByteArray()

    @Before
    fun setUp() {
        tmpDir =
            File.createTempFile("legacytest", "").apply {
                delete()
                mkdirs()
            }
    }

    @Test
    fun detect_recognizesAllThreeLegacyFormats() {
        val sdb1 = File(tmpDir, "v1.sdb").apply { sealV1("hello v1", passphrase, this) }
        val sdb2 = File(tmpDir, "v2.sdb").apply { sealV2("hello v2", passphrase, this) }
        val plainZip = File(tmpDir, "plain.zip").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0)) }
        val unknown = File(tmpDir, "unknown.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        assertEquals(LegacySdbCodec.LegacyFormat.SDB1_GCM, LegacySdbCodec.detect(sdb1))
        assertEquals(LegacySdbCodec.LegacyFormat.SDB2_CTR, LegacySdbCodec.detect(sdb2))
        assertEquals(LegacySdbCodec.LegacyFormat.PLAIN_ZIP, LegacySdbCodec.detect(plainZip))
        assertNull(LegacySdbCodec.detect(unknown))
    }

    @Test
    fun decodeSdb1_roundTrip() {
        val content = "旧版 SDB1 GCM 内容 — legacy content"
        val sealed = File(tmpDir, "v1.sdb").apply { sealV1(content, passphrase, this) }
        val output = File(tmpDir, "out.bin")

        LegacySdbCodec.decodeSdb1(sealed, output, passphrase, sealed.path)

        assertArrayEquals(content.toByteArray(), output.readBytes())
    }

    @Test
    fun decodeSdb2_roundTrip() {
        val content = "旧版 SDB2 CTR 内容 — legacy content"
        val sealed = File(tmpDir, "v2.sdb").apply { sealV2(content, passphrase, this) }
        val output = File(tmpDir, "out.bin")

        LegacySdbCodec.decodeSdb2(sealed, output, passphrase, sealed.path)

        assertArrayEquals(content.toByteArray(), output.readBytes())
    }

    @Test
    fun decodeSdb1_wrongPassphrase_throwsAuthenticationFailed() {
        val sealed = File(tmpDir, "v1.sdb").apply { sealV1("secret", passphrase, this) }
        assertThrows(BackupFormatException.AuthenticationFailed::class.java) {
            LegacySdbCodec.decodeSdb1(sealed, File(tmpDir, "out.bin"), "wrong".toByteArray(), sealed.path)
        }
    }

    @Test
    fun decodeSdb2_wrongPassphrase_throwsAuthenticationFailed() {
        val sealed = File(tmpDir, "v2.sdb").apply { sealV2("secret", passphrase, this) }
        assertThrows(BackupFormatException.AuthenticationFailed::class.java) {
            LegacySdbCodec.decodeSdb2(sealed, File(tmpDir, "out.bin"), "wrong".toByteArray(), sealed.path)
        }
    }

    // ── 造夹具用的编码器：与 LegacySdbCodec 的私有 deriveAesKey/deriveHmacKey 逐字节一致 ──

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
        val aesKey = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
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
        val aesKey = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(content.toByteArray())
        val payload = salt + iv + ciphertext
        val hmac = Mac.getInstance("HmacSHA256").apply { init(deriveHmacKey(passphrase, salt)) }.doFinal(payload)
        output.writeBytes("SDB2".toByteArray(Charsets.US_ASCII) + hmac + payload)
    }
}
