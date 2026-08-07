package io.sanato.appkit.backup.format

import io.sanato.appkit.backup.core.PassphraseProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.CRC32

class Sbk1BackupCodecTest {
    private lateinit var tmpDir: File
    private val codec = Sbk1BackupCodec()
    private val passphrase = PassphraseProvider { "correct horse battery staple".toByteArray() }
    private val wrongPassphrase = PassphraseProvider { "wrong passphrase".toByteArray() }

    @Before
    fun setUp() {
        tmpDir =
            File.createTempFile("sbk1test", "").apply {
                delete()
                mkdirs()
            }
    }

    private fun plainFile(content: String): File =
        File(tmpDir, "plain_${System.nanoTime()}.bin").apply { writeBytes(content.toByteArray()) }

    private suspend inline fun <reified T : Throwable> assertFailsWith(crossinline block: suspend () -> Unit): T {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue("expected ${T::class.simpleName} but got $error", error is T)
        return error as T
    }

    @Test
    fun sealThenUnseal_roundTrip() =
        runTest {
            val plain = plainFile("hello sbk1 — 你好，世界")
            val sealed = File(tmpDir, "sealed.sbk")
            codec.seal(plain, sealed, passphrase)

            val output = File(tmpDir, "output.bin")
            codec.unseal(sealed, output, listOf(passphrase))

            assertArrayEquals(plain.readBytes(), output.readBytes())
        }

    @Test
    fun unseal_triesCandidatesInOrder_firstMatchWins() =
        runTest {
            val plain = plainFile("multi-candidate")
            val sealed = File(tmpDir, "sealed.sbk")
            codec.seal(plain, sealed, passphrase)

            val output = File(tmpDir, "output.bin")
            codec.unseal(sealed, output, listOf(wrongPassphrase, passphrase))

            assertArrayEquals(plain.readBytes(), output.readBytes())
        }

    @Test
    fun unseal_wrongPassphrase_throwsAuthenticationFailed() =
        runTest {
            val plain = plainFile("secret")
            val sealed = File(tmpDir, "sealed.sbk")
            codec.seal(plain, sealed, passphrase)

            assertFailsWith<BackupFormatException.AuthenticationFailed> {
                codec.unseal(sealed, File(tmpDir, "out.bin"), listOf(wrongPassphrase))
            }
        }

    @Test
    fun inspect_readsHeaderWithoutPassphrase() =
        runTest {
            val plain = plainFile("inspect me")
            val sealed = File(tmpDir, "sealed.sbk")
            codec.seal(plain, sealed, passphrase, SealOptions(producer = "unit-test/1.0"))

            val info = codec.inspect(sealed)

            assertEquals(BackupAlgorithms.FORMAT_VERSION, info.formatVersion)
            assertEquals(BackupAlgorithms.CIPHER_AES256_CTR, info.cipherId)
            assertEquals(BackupAlgorithms.MAC_HMAC_SHA256, info.macId)
            assertEquals(BackupAlgorithms.KDF_PBKDF2_HMAC_SHA256, info.kdfId)
            assertEquals("unit-test/1.0", info.producer)
            assertFalse(info.isLegacyFormat)
        }

    @Test
    fun unseal_unknownFormatVersion_throwsUnsupportedFormatVersion() =
        runTest {
            val plain = plainFile("version test")
            val sealed = File(tmpDir, "sealed.sbk")
            codec.seal(plain, sealed, passphrase)
            patchByteAndFixCrc(sealed, offset = 4, newValue = 99)

            assertFailsWith<BackupFormatException.UnsupportedFormatVersion> {
                codec.unseal(sealed, File(tmpDir, "out.bin"), listOf(passphrase))
            }
        }

    @Test
    fun unseal_unknownCipherId_throwsUnsupportedAlgorithm() =
        runTest {
            val plain = plainFile("cipher test")
            val sealed = File(tmpDir, "sealed.sbk")
            codec.seal(plain, sealed, passphrase)
            patchByteAndFixCrc(sealed, offset = 5, newValue = 99)

            assertFailsWith<BackupFormatException.UnsupportedAlgorithm> {
                codec.unseal(sealed, File(tmpDir, "out.bin"), listOf(passphrase))
            }
        }

    /**
     * MAC 覆盖整个头部，不只是 CRC 能护住的完整性——篡改一个头部字段并同步修正 CRC32
     * （模拟"看起来没有意外损坏"的篡改），仍然必须在 MAC 校验这关被挡下来，而不是
     * 静默用错误的算法尝试解密。这是 SBK1 相对 legacy 格式(HMAC 只盖 salt‖iv‖ciphertext，
     * 算法标识完全不受保护)的核心安全提升。
     */
    @Test
    fun unseal_tamperedHeaderFieldWithFixedCrc_stillCaughtByMac() =
        runTest {
            val plain = plainFile("tamper test")
            val sealed = File(tmpDir, "sealed.sbk")
            codec.seal(plain, sealed, passphrase)
            // kdfIterations 字段（offset 12-15，大端）改最低位字节，同步修正 CRC，让
            // "看起来没坏"——特意改最低位而不是最高位：改最高位会让迭代次数暴涨到上亿，
            // PBKDF2 计算耗时暴涨拖垮测试，不是这个用例想验证的东西。
            patchByteAndFixCrc(sealed, offset = 15, newValue = 7)

            assertFailsWith<BackupFormatException.AuthenticationFailed> {
                codec.unseal(sealed, File(tmpDir, "out.bin"), listOf(passphrase))
            }
        }

    @Test
    fun unseal_corruptedHeaderCrc_throwsHeaderCorrupted() =
        runTest {
            val plain = plainFile("crc test")
            val sealed = File(tmpDir, "sealed.sbk")
            codec.seal(plain, sealed, passphrase)
            // 直接改字节但不修正 CRC —— 模拟意外损坏。
            RandomAccessFile(sealed, "rw").use { raf ->
                raf.seek(20)
                raf.write(0xFF)
            }

            assertFailsWith<BackupFormatException.HeaderCorrupted> {
                codec.unseal(sealed, File(tmpDir, "out.bin"), listOf(passphrase))
            }
        }

    @Test
    fun unseal_notABackupFile_throwsNotABackupFile() =
        runTest {
            val randomFile = File(tmpDir, "random.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)) }
            assertFailsWith<BackupFormatException.NotABackupFile> {
                codec.unseal(randomFile, File(tmpDir, "out.bin"), listOf(passphrase))
            }
        }

    /** 恒定内存回归的机械防线之一：大文件（这里用几 MB 代表）往返不出错、字节一致。 */
    @Test
    fun sealThenUnseal_largerPayload_roundTrip() =
        runTest {
            val plain = File(tmpDir, "large.bin")
            val random = java.security.SecureRandom()
            val bytes = ByteArray(4 * 1024 * 1024)
            random.nextBytes(bytes)
            plain.writeBytes(bytes)

            val sealed = File(tmpDir, "sealed.sbk")
            codec.seal(plain, sealed, passphrase)
            val output = File(tmpDir, "output.bin")
            codec.unseal(sealed, output, listOf(passphrase))

            assertArrayEquals(bytes, output.readBytes())
        }

    /** 头部字段(offset,1 字节)被篡改后，重新计算并回填 headerCrc32，模拟"看起来没有意外损坏"的篡改。 */
    private fun patchByteAndFixCrc(
        file: File,
        offset: Int,
        newValue: Int,
    ) {
        RandomAccessFile(file, "rw").use { raf ->
            val headerLenBytes = ByteArray(2)
            raf.seek(8)
            raf.readFully(headerLenBytes)
            val headerLength = ((headerLenBytes[0].toInt() and 0xFF) shl 8) or (headerLenBytes[1].toInt() and 0xFF)

            raf.seek(offset.toLong())
            raf.write(newValue)

            val full = ByteArray(headerLength)
            raf.seek(0)
            raf.readFully(full)
            val crc = CRC32()
            crc.update(full, 0, 32)
            crc.update(full, 36, headerLength - 36)
            val crcBytes =
                byteArrayOf(
                    (crc.value shr 24).toByte(),
                    (crc.value shr 16).toByte(),
                    (crc.value shr 8).toByte(),
                    crc.value.toByte(),
                )
            raf.seek(32)
            raf.write(crcBytes)
        }
    }
}
