package io.sanato.logkit

import java.security.MessageDigest

/**
 * ⚠️⚠️⚠️ 这是本模板仓库的 THROWAWAY DEBUG 密钥,不是生产密钥。
 *
 * 私钥就是 `logkit/keys/debug-private-key.pem`(同样签入仓库,仅用于本模板的
 * 测试与本地验证)。任何 fork 这个仓库的人,如果不换掉这个公钥就发布 App,
 * 等于把自己用户的日志加密给了"模板作者能解密"的密钥——见 TEMPLATE.md 的
 * fork checklist 第 8 步 `scripts/logkit-keygen.sh`。
 *
 * 生成方式(见 scripts/logkit-keygen.sh):
 *   openssl ecparam -name prime256v1 -genkey -noout -out debug-private-key.pem
 *   openssl pkey -in debug-private-key.pem -pubout -outform DER
 */
internal object BuiltInRecipientKey {
    /** P-256 SubjectPublicKeyInfo,DER 编码,91 字节。 */
    val PUBLIC_KEY_SPKI_DER: ByteArray =
        byteArrayOf(
            48,
            89,
            48,
            19,
            6,
            7,
            42,
            -122,
            72,
            -50,
            61,
            2,
            1,
            6,
            8,
            42,
            -122,
            72,
            -50,
            61,
            3,
            1,
            7,
            3,
            66,
            0,
            4,
            10,
            -20,
            -84,
            58,
            -114,
            -34,
            42,
            122,
            97,
            65,
            119,
            -98,
            81,
            86,
            -7,
            107,
            -79,
            125,
            -102,
            -42,
            116,
            57,
            -25,
            -22,
            -49,
            64,
            -19,
            66,
            -20,
            12,
            90,
            62,
            -2,
            11,
            91,
            92,
            -49,
            25,
            44,
            38,
            4,
            81,
            14,
            74,
            127,
            57,
            -74,
            -42,
            -61,
            116,
            126,
            -70,
            81,
            -101,
            38,
            5,
            -95,
            118,
            -95,
            -52,
            -55,
            -88,
            121,
            60,
        )

    /** SHA-256(SPKI DER) 的前 4 字节,作为 uint32 BE——写进文件头的 `keyId` 字段。 */
    val KEY_ID: Int by lazy {
        val digest = MessageDigest.getInstance("SHA-256").digest(PUBLIC_KEY_SPKI_DER)
        (digest[0].toInt() and 0xFF shl 24) or
            (digest[1].toInt() and 0xFF shl 16) or
            (digest[2].toInt() and 0xFF shl 8) or
            (digest[3].toInt() and 0xFF)
    }
}
