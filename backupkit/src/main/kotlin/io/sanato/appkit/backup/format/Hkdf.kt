package io.sanato.appkit.backup.format

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 5869 HKDF-Expand-SHA256（不含 Extract 步骤——喂给它的 [prk] 已经是 PBKDF2 的
 * 256-bit 输出，本身就是均匀分布的伪随机密钥，符合 HKDF-Expand 对输入的假设）。
 *
 * 用于从同一个主密钥派生出互相独立的加密子密钥与 MAC 子密钥，域分隔靠 [info] 标签，
 * 比 legacy 那种"直接对原始口令做一次 SHA-256"更规范：两把子密钥都来自同一个已拉伸的
 * 主密钥，而不是从口令直接、独立地各推一次。
 */
internal object Hkdf {
    private const val HASH_LEN = 32 // SHA-256 输出长度

    fun expandSha256(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(length <= 255 * HASH_LEN) { "HKDF-Expand output too long: $length" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))

        val output = ByteArray(length)
        var previousBlock = ByteArray(0)
        var generated = 0
        var counter = 1
        while (generated < length) {
            mac.reset()
            mac.update(previousBlock)
            mac.update(info)
            mac.update(counter.toByte())
            val block = mac.doFinal()
            val toCopy = minOf(block.size, length - generated)
            System.arraycopy(block, 0, output, generated, toCopy)
            generated += toCopy
            previousBlock = block
            counter++
        }
        return output
    }
}
