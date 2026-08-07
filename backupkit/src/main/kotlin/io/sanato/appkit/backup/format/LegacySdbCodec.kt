package io.sanato.appkit.backup.format

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 只读的历史格式解码器——覆盖迁移前 app 产出的三种旧包：`SDB1`(AES-GCM，全量入内存)、
 * `SDB2`(AES-CTR+HMAC，流式)、裸 zip（未加密，直接是 manifest+media 的 zip 包）。
 *
 * backupkit **只写新格式**，这里的每个函数都不可能被写路径调用——存在的唯一理由是
 * 云端/本地已经有大量这三种历史格式的存量数据，SDK 必须能读懂它们，不能造成数据断层。
 *
 * 密钥派生与容器布局是对旧 app 实现（`JournalBackupManager.kt` 的 `seal`/`unsealV1Legacy`/
 * `unsealV2`/`deriveAesKey`/`deriveHmacKey`）的逐字节复刻，不是重新设计——任何看起来
 * "不够规范"的地方（比如口令字符按 `toInt().toChar()` 直接映射、MAC 只覆盖
 * `salt||iv||ciphertext` 不含头部）都是历史事实，只能原样复刻，不能"顺手改进"。
 */
internal object LegacySdbCodec {
    private const val MAGIC_V1 = "SDB1"
    private const val MAGIC_V2 = "SDB2"
    private const val SALT_SIZE = 16
    private const val IV_V1_SIZE = 12
    private const val IV_V2_SIZE = 16
    private const val HMAC_SIZE = 32
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_SIZE_BITS = 256
    private const val HEADER_V1_SIZE = 4 + HMAC_SIZE + SALT_SIZE + IV_V1_SIZE
    private const val HEADER_V2_SIZE = 4 + HMAC_SIZE + SALT_SIZE + IV_V2_SIZE

    enum class LegacyFormat { SDB1_GCM, SDB2_CTR, PLAIN_ZIP }

    /** 按文件头 magic 判定属于哪种历史格式；都不是则返回 null（调用方据此判定"不认识这个文件"）。 */
    fun detect(file: File): LegacyFormat? {
        if (isPlainZip(file)) return LegacyFormat.PLAIN_ZIP
        if (file.length() < 4) return null
        val magic = ByteArray(4)
        FileInputStream(file).use { input -> if (input.read(magic) != 4) return null }
        return when (String(magic, Charsets.US_ASCII)) {
            MAGIC_V1 -> LegacyFormat.SDB1_GCM
            MAGIC_V2 -> LegacyFormat.SDB2_CTR
            else -> null
        }
    }

    private fun isPlainZip(file: File): Boolean {
        if (!file.isFile || file.length() < 4L) return false
        return runCatching {
            FileInputStream(file).use { input ->
                val header = ByteArray(4)
                if (input.read(header) != header.size) return@runCatching false
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                    (
                        (header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) ||
                            (header[2] == 0x05.toByte() && header[3] == 0x06.toByte()) ||
                            (header[2] == 0x07.toByte() && header[3] == 0x08.toByte())
                    )
            }
        }.getOrDefault(false)
    }

    fun decodeSdb1(
        sealedFile: File,
        outputFile: File,
        passphrase: ByteArray,
        path: String,
    ) {
        // 与原实现一致：GCM 全量入内存，只对旧的、entry 级小文件安全——不是本解码器的
        // 缺陷，是 Conscrypt 的 AES/GCM 本身无法真流式，历史遗留限制原样保留。
        val allBytes = sealedFile.readBytes()
        if (allBytes.size < HEADER_V1_SIZE) throw BackupFormatException.Truncated(path)
        val storedHmac = allBytes.copyOfRange(4, 4 + HMAC_SIZE)
        val payload = allBytes.copyOfRange(4 + HMAC_SIZE, allBytes.size)
        val salt = payload.copyOfRange(0, SALT_SIZE)
        val iv = payload.copyOfRange(SALT_SIZE, SALT_SIZE + IV_V1_SIZE)
        val ciphertext = payload.copyOfRange(SALT_SIZE + IV_V1_SIZE, payload.size)

        val hmacKey = deriveHmacKey(passphrase, salt)
        val computedHmac = Mac.getInstance("HmacSHA256").apply { init(hmacKey) }.doFinal(payload)
        if (!MessageDigest.isEqual(storedHmac, computedHmac)) {
            throw BackupFormatException.AuthenticationFailed(path)
        }

        val aesKey = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        outputFile.writeBytes(cipher.doFinal(ciphertext))
    }

    fun decodeSdb2(
        sealedFile: File,
        outputFile: File,
        passphrase: ByteArray,
        path: String,
    ) {
        if (sealedFile.length() < HEADER_V2_SIZE) throw BackupFormatException.Truncated(path)
        val storedHmac = ByteArray(HMAC_SIZE)
        val salt = ByteArray(SALT_SIZE)
        val iv = ByteArray(IV_V2_SIZE)
        DataInputStream(FileInputStream(sealedFile)).use { dis ->
            dis.skipBytes(4)
            dis.readFully(storedHmac)
            dis.readFully(salt)
            dis.readFully(iv)
        }

        val hmacKey = deriveHmacKey(passphrase, salt)
        val mac = Mac.getInstance("HmacSHA256").apply { init(hmacKey) }
        FileInputStream(sealedFile).use { fis ->
            skipExact(fis, (4 + HMAC_SIZE).toLong())
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = fis.read(buf)
                if (n <= 0) break
                mac.update(buf, 0, n)
            }
        }
        val computedHmac = mac.doFinal()
        if (!MessageDigest.isEqual(storedHmac, computedHmac)) {
            throw BackupFormatException.AuthenticationFailed(path)
        }

        val aesKey = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, IvParameterSpec(iv))
        FileInputStream(sealedFile).use { fis ->
            skipExact(fis, (4 + HMAC_SIZE + SALT_SIZE + IV_V2_SIZE).toLong())
            CipherInputStream(fis, cipher).use { cis ->
                FileOutputStream(outputFile).use { fos -> cis.copyTo(fos, bufferSize = 64 * 1024) }
            }
        }
    }

    private fun skipExact(
        input: java.io.InputStream,
        target: Long,
    ) {
        var skipped = 0L
        while (skipped < target) {
            val s = input.skip(target - skipped)
            if (s <= 0) throw IllegalStateException("Unexpected EOF while skipping $target bytes")
            skipped += s
        }
    }

    private fun deriveAesKey(
        passphrase: ByteArray,
        salt: ByteArray,
    ): SecretKeySpec {
        val spec =
            PBEKeySpec(passphrase.map { it.toInt().toChar() }.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE_BITS)
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
        md.update(byteArrayOf(0x48, 0x4D)) // "HM" 域分隔符，与旧实现一致
        return SecretKeySpec(md.digest(), "HmacSHA256")
    }
}
