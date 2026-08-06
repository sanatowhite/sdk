package io.sanato.logkit.format

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** RFC 5869 官方测试向量——HKDF 算错是静默的、影响全部历史日志的全损故障。 */
class HkdfTest {
    @Test
    fun `case 1 basic SHA-256`() {
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")

        val prk = Hkdf.extract(salt, ikm)
        assertArrayEquals(hex("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5"), prk)

        val okm = Hkdf.expand(prk, info, 42)
        assertArrayEquals(
            hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
            okm,
        )
    }

    @Test
    fun `case 3 zero-length salt and info`() {
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = ByteArray(0)
        val info = ByteArray(0)

        val prk = Hkdf.extract(salt, ikm)
        val okm = Hkdf.expand(prk, info, 42)
        assertArrayEquals(
            hex("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"),
            okm,
        )
    }

    @Test
    fun `expand beyond a single HMAC block produces the correct multi-block output`() {
        val prk = Hkdf.extract(ByteArray(0), "some input keying material".toByteArray())
        val okm = Hkdf.expand(prk, "info".toByteArray(), 80)
        assertEquals(80, okm.size)
        // 多算一次、只取前 32 字节,必须和单独要 32 字节时完全一致——block 边界不能算错。
        val okm32 = Hkdf.expand(prk, "info".toByteArray(), 32)
        assertArrayEquals(okm.copyOfRange(0, 32), okm32)
    }

    private fun assertEquals(
        expected: Int,
        actual: Int,
    ) = org.junit.Assert.assertEquals(expected, actual)

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { i ->
            ((Character.digit(s[i * 2], 16) shl 4) + Character.digit(s[i * 2 + 1], 16)).toByte()
        }
}
