package io.sanato.logkit.format

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ECIES 风格混合信封：每个日志文件一个随机 AES-256-GCM 内容密钥,用内置(或宿主
 * 配置的)P-256 公钥包裹。选 ECIES 而不是 RSA-OAEP 的理由记在 logkit/README.md
 * 和 docs/adr/0010——核心是 Android 的 JCE provider 历史上会无视请求的 OAEP
 * MGF1 摘要而默认 SHA-1,桌面 JVM 用 SHA-256,设备上加密的文件在开发者机器上
 * 会【永久】解不开。ECIES 的曲线/KDF/AEAD 全由这里的字节钉死,没有这个参数面。
 *
 * kemId = 1(见 [FileHeaderCodec])对应本文件的算法组合:
 *   eph = 一次性 EC(secp256r1) 密钥对
 *   z   = ECDH(ephPrivate, recipientPublic)          32 字节共享秘密
 *   kek = HKDF-SHA256(salt=常量, ikm=z, info=常量||fileSeq, L=32)
 *   wrapped = AES-256-GCM(kek, kwNonce, aad).seal(contentKey)
 *
 * `wrappedKey` 125 字节 = ephPubPoint(65) ‖ kwNonce(12) ‖ (ciphertext‖tag)(48)。
 */
internal object Envelope {
    const val KEM_ID_ECIES_P256: Byte = 1

    private const val CURVE_NAME = "secp256r1"
    private const val POINT_LEN = 65 // 0x04 || X(32) || Y(32)
    private const val KW_NONCE_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val CONTENT_KEY_LEN = 32
    const val WRAPPED_KEY_LEN = POINT_LEN + KW_NONCE_LEN + CONTENT_KEY_LEN + (GCM_TAG_BITS / 8) // 125

    private val HKDF_SALT = "logkit/v1/salt".toByteArray(Charsets.US_ASCII)
    private val HKDF_INFO_PREFIX = "logkit/v1/filekey".toByteArray(Charsets.US_ASCII)

    private val ecParameterSpec: ECParameterSpec by lazy {
        val params = AlgorithmParameters.getInstance("EC")
        params.init(ECGenParameterSpec(CURVE_NAME))
        params.getParameterSpec(ECParameterSpec::class.java)
    }

    fun generateRecipientKeyPair(random: SecureRandom = SecureRandom()): java.security.KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(CURVE_NAME), random)
        return generator.generateKeyPair()
    }

    /** 生成随机 32 字节 AES-256-GCM 内容密钥。 */
    fun generateContentKey(random: SecureRandom = SecureRandom()): ByteArray {
        val key = ByteArray(CONTENT_KEY_LEN)
        random.nextBytes(key)
        return key
    }

    /**
     * 用收件方公钥包裹 [contentKey],返回 125 字节 `wrappedKey` blob。
     * [fileSeq] 绑定进 AAD——防止一个文件的 wrappedKey 被拼接到另一个文件头上。
     */
    fun wrap(
        recipientPublicKey: PublicKey,
        contentKey: ByteArray,
        keyId: Int,
        fileSeq: Long,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(contentKey.size == CONTENT_KEY_LEN) { "content key must be $CONTENT_KEY_LEN bytes" }
        val ephemeral =
            KeyPairGenerator
                .getInstance(
                    "EC",
                ).apply { initialize(ecParameterSpec, random) }
                .generateKeyPair()
        val ephPoint = encodePoint(ephemeral.public as java.security.interfaces.ECPublicKey)

        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(ephemeral.private)
        agreement.doPhase(recipientPublicKey, true)
        val sharedSecret = agreement.generateSecret()

        val kek = deriveKek(sharedSecret, keyId, fileSeq)
        val kwNonce = ByteArray(KW_NONCE_LEN).also { random.nextBytes(it) }
        val aad = wrapAad(ephPoint, keyId, fileSeq)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_BITS, kwNonce))
        cipher.updateAAD(aad)
        val sealed = cipher.doFinal(contentKey) // ciphertext(32) || tag(16) = 48 bytes

        return ephPoint + kwNonce + sealed
    }

    /** 逆操作。密钥不匹配、AAD 不匹配或篡改都会抛 [GeneralSecurityException]。 */
    fun unwrap(
        recipientPrivateKey: PrivateKey,
        wrappedKey: ByteArray,
        keyId: Int,
        fileSeq: Long,
    ): ByteArray {
        require(wrappedKey.size == WRAPPED_KEY_LEN) { "wrapped key must be $WRAPPED_KEY_LEN bytes" }
        val ephPoint = wrappedKey.copyOfRange(0, POINT_LEN)
        val kwNonce = wrappedKey.copyOfRange(POINT_LEN, POINT_LEN + KW_NONCE_LEN)
        val sealed = wrappedKey.copyOfRange(POINT_LEN + KW_NONCE_LEN, wrappedKey.size)

        val ephPublicKey = decodePoint(ephPoint)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(recipientPrivateKey)
        agreement.doPhase(ephPublicKey, true)
        val sharedSecret = agreement.generateSecret()

        val kek = deriveKek(sharedSecret, keyId, fileSeq)
        val aad = wrapAad(ephPoint, keyId, fileSeq)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_BITS, kwNonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed)
    }

    /**
     * 生产路径真正调用的自检——只用对称原语,不需要私钥(私钥离线,设备上根本
     * 没有)。用刚生成的内容密钥,以【完全相同的】`Cipher`/`GCMParameterSpec`
     * 构造做一次 AES-256-GCM 加密再解密并比对,在建文件、写任何数据之前跑一次
     * (~0.1ms)。它验证的是"AES/GCM/NoPadding 这个 transformation 字符串在本设备
     * provider 上到底可用",这正是任何单元测试都答不了、只有真机才能证明的
     * 那件事——见 logkit/README.md 的 Robolectric 局限说明。
     *
     * 不验证 ECDH/HKDF 包裹路径本身(那需要私钥才能解开,设备上没有);
     * 但 [wrap] 里的 `KeyAgreement.getInstance("ECDH")`/EC 密钥生成本身若失败会
     * 直接抛 [GeneralSecurityException],调用方(`Crypto.newFileKeys`)已经把
     * 整个建档流程包在 try/catch 里,同样归入 cryptoUnavailable。
     */
    fun selfProbeSymmetric(random: SecureRandom = SecureRandom()): Boolean =
        try {
            val key = generateContentKey(random)
            val nonce = ByteArray(KW_NONCE_LEN).also { random.nextBytes(it) }
            val probe = ByteArray(16).also { random.nextBytes(it) }

            val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
            encryptCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            val sealed = encryptCipher.doFinal(probe)

            val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
            decryptCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            val opened = decryptCipher.doFinal(sealed)

            opened.contentEquals(probe)
        } catch (e: GeneralSecurityException) {
            false
        }

    /**
     * 测试专用的完整 wrap→unwrap 自检(需要私钥,只有测试环境才同时持有两半)。
     * 生产路径用上面的 [selfProbeSymmetric],理由见其文档。
     */
    fun selfProbe(
        recipientPublicKey: PublicKey,
        recipientPrivateKeyForProbe: PrivateKey,
        keyId: Int,
        fileSeq: Long,
        random: SecureRandom = SecureRandom(),
    ): Boolean =
        try {
            val probeKey = generateContentKey(random)
            val wrapped = wrap(recipientPublicKey, probeKey, keyId, fileSeq, random)
            val roundTripped = unwrap(recipientPrivateKeyForProbe, wrapped, keyId, fileSeq)
            probeKey.contentEquals(roundTripped)
        } catch (e: GeneralSecurityException) {
            false
        }

    private fun deriveKek(
        sharedSecret: ByteArray,
        keyId: Int,
        fileSeq: Long,
    ): ByteArray {
        val info =
            HKDF_INFO_PREFIX + byteArrayOf(0) +
                intToBytes(keyId) +
                longToBytes(fileSeq)
        val prk = Hkdf.extract(HKDF_SALT, sharedSecret)
        return Hkdf.expand(prk, info, CONTENT_KEY_LEN)
    }

    private fun wrapAad(
        ephPoint: ByteArray,
        keyId: Int,
        fileSeq: Long,
    ): ByteArray = ephPoint + intToBytes(keyId) + longToBytes(fileSeq)

    private fun encodePoint(publicKey: java.security.interfaces.ECPublicKey): ByteArray {
        val fieldSize = (publicKey.params.curve.field.fieldSize + 7) / 8
        val x = unsignedFixedLength(publicKey.w.affineX, fieldSize)
        val y = unsignedFixedLength(publicKey.w.affineY, fieldSize)
        return byteArrayOf(0x04) + x + y
    }

    private fun decodePoint(pointBytes: ByteArray): PublicKey {
        require(pointBytes.size == POINT_LEN && pointBytes[0] == 0x04.toByte()) {
            "expected 65-byte uncompressed EC point"
        }
        val fieldSize = 32
        val x = BigInteger(1, pointBytes.copyOfRange(1, 1 + fieldSize))
        val y = BigInteger(1, pointBytes.copyOfRange(1 + fieldSize, 1 + 2 * fieldSize))
        val spec = ECPublicKeySpec(ECPoint(x, y), ecParameterSpec)
        return KeyFactory.getInstance("EC").generatePublic(spec)
    }

    private fun unsignedFixedLength(
        value: BigInteger,
        length: Int,
    ): ByteArray {
        val raw = value.toByteArray()
        val trimmed = if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        require(trimmed.size <= length) { "coordinate too large for fixed field size" }
        val out = ByteArray(length)
        System.arraycopy(trimmed, 0, out, length - trimmed.size, trimmed.size)
        return out
    }

    private fun intToBytes(value: Int): ByteArray =
        byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )

    private fun longToBytes(value: Long): ByteArray = ByteArray(8) { i -> (value ushr ((7 - i) * 8)).toByte() }
}
