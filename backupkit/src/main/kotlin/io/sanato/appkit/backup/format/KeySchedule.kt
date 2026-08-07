package io.sanato.appkit.backup.format

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 口令 → 主密钥 → (加密子密钥, MAC 子密钥) 的完整派生链：
 *
 * ```
 * pwChars = hex(SHA-256(passphraseBytes))          # 64 个 ASCII 字符
 * master  = PBKDF2-HMAC-SHA256(pwChars, salt, iterations, keyLength)
 * encKey  = HKDF-Expand-SHA256(master, "SBK1/enc/v1", 32)
 * macKey  = HKDF-Expand-SHA256(master, "SBK1/mac/v1", 32)
 * ```
 *
 * 为什么要先对口令做一次 SHA-256 再转十六进制字符串：`PBEKeySpec` 只接受 `char[]`，
 * 如果直接把原始字节按 `byte.toInt().toChar()` 映射成 char（很多实现的自然写法），
 * `PBEKeySpec` 内部会再按 UTF-8 把这些 char 编码回字节——字节值 ≥0x80 时会膨胀成
 * 2 字节，这个行为在不同平台/语言的 UTF-8 实现之间极易产生分歧。先做 SHA-256 拿到
 * 64 个十六进制 ASCII 字符（值域 0x30-0x66，全部落在 ASCII 范围内），char→UTF-8→字节
 * 是严格的 1:1 恒等映射，任何语言都能不差一位地复现。
 */
internal object KeySchedule {
    private const val ENC_INFO = "SBK1/enc/v1"
    private const val MAC_INFO = "SBK1/mac/v1"

    fun deriveMasterKey(
        passphrase: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBytes: Int,
    ): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(passphrase)
        val hex = digest.joinToString("") { "%02x".format(it) }
        val spec = PBEKeySpec(hex.toCharArray(), salt, iterations, keyLengthBytes * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun deriveEncKey(master: ByteArray): SecretKeySpec =
        SecretKeySpec(Hkdf.expandSha256(master, ENC_INFO.toByteArray(Charsets.US_ASCII), 32), "AES")

    fun deriveMacKey(master: ByteArray): SecretKeySpec =
        SecretKeySpec(Hkdf.expandSha256(master, MAC_INFO.toByteArray(Charsets.US_ASCII), 32), "HmacSHA256")

    /** 尽力擦除敏感字节数组。`SecretKeySpec`/JCE 内部的拷贝无法从外部擦除，这是已知限制，不是假装做到了。 */
    fun wipe(bytes: ByteArray) {
        bytes.fill(0)
    }
}
