package io.sanato.logkit.format

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 5869 (HMAC-based Key Derivation Function), HMAC-SHA256 only.
 *
 * 这是本模块里唯一手写的密码学原语——JDK 没有内置 HKDF。选它而不是引入
 * BouncyCastle/Tink 是 [io.sanato.logkit] "零第三方依赖"这条铁律的直接代价,
 * 见 logkit/README.md 的风险小节。正确性由 [HkdfTest] 里的 RFC 5869 官方
 * 测试向量兜底——HKDF 算错是静默的、影响全部历史日志的全损故障,不是可以
 * "先跑起来再修"的 bug。
 *
 * 本模块只需要 L <= 32(单块 `expand`),但循环按 RFC 5869 通用形式实现并
 * 测试到 L > 32,以免日后有人在真正需要多块时把它实现错。
 */
internal object Hkdf {
    private const val HMAC_SHA256 = "HmacSHA256"
    private const val HASH_LEN = 32

    /** RFC 5869 §2.2 —— salt 为空时用 HASH_LEN 个零字节代替,不是跳过这一步。 */
    fun extract(
        salt: ByteArray,
        ikm: ByteArray,
    ): ByteArray {
        val effectiveSalt = if (salt.isEmpty()) ByteArray(HASH_LEN) else salt
        return hmac(effectiveSalt, ikm)
    }

    /** RFC 5869 §2.3 —— L 上界 255*HashLen,本模块里 L 从不超过 32。 */
    fun expand(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(length in 1..(255 * HASH_LEN)) { "HKDF expand length out of range: $length" }
        val output = ByteArray(length)
        var previousBlock = ByteArray(0)
        var generated = 0
        var counter = 1
        while (generated < length) {
            val mac = Mac.getInstance(HMAC_SHA256)
            mac.init(SecretKeySpec(prk, HMAC_SHA256))
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

    private fun hmac(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(key, HMAC_SHA256))
        return mac.doFinal(data)
    }
}
