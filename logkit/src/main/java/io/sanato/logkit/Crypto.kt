package io.sanato.logkit

import io.sanato.logkit.format.Envelope
import io.sanato.logkit.format.FrameCodec
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec

/** 一个文件的全部密钥材料——内容密钥只在内存里活,从不落盘。 */
internal data class FileKeys(
    val contentKey: ByteArray,
    val wrappedKey: ByteArray,
    val nonceSalt: ByteArray,
    val keyId: Int,
)

/**
 * 确定性测试接缝。生产路径是 [RealCrypto];测试用手写 fake(如 IdentityCrypto)
 * 让分帧/滚动/淘汰测试完全不碰密钥。
 */
internal interface Crypto {
    /** 建新文件时调用一次。返回 null 表示 crypto 不可用——调用方必须放弃建这个文件,
     * 绝不退化为明文(见 [io.sanato.logkit.format.Envelope] 的自检文档)。 */
    fun newFileKeys(fileSeq: Long): FileKeys?

    fun sealFrame(
        keys: FileKeys,
        frameIndex: Long,
        firstRecordSeq: Long,
        recordCount: Int,
        plaintext: ByteArray,
        compress: Boolean,
        flushInduced: Boolean,
    ): ByteArray
}

internal class RealCrypto(
    private val recipientPublicKeyDer: ByteArray,
    private val keyId: Int,
    private val random: SecureRandom = SecureRandom(),
) : Crypto {
    private val recipientPublicKey: PublicKey by lazy {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(recipientPublicKeyDer))
    }

    override fun newFileKeys(fileSeq: Long): FileKeys? =
        try {
            if (!Envelope.selfProbeSymmetric(random)) {
                null
            } else {
                val contentKey = Envelope.generateContentKey(random)
                val wrappedKey = Envelope.wrap(recipientPublicKey, contentKey, keyId, fileSeq, random)
                val nonceSalt = ByteArray(4).also { random.nextBytes(it) }
                FileKeys(contentKey, wrappedKey, nonceSalt, keyId)
            }
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            // KeyFactory/X509EncodedKeySpec 对畸形 DER 抛这个,同样归入 crypto 不可用。
            null
        }

    override fun sealFrame(
        keys: FileKeys,
        frameIndex: Long,
        firstRecordSeq: Long,
        recordCount: Int,
        plaintext: ByteArray,
        compress: Boolean,
        flushInduced: Boolean,
    ): ByteArray =
        FrameCodec.seal(
            keys.contentKey,
            keys.nonceSalt,
            frameIndex,
            firstRecordSeq,
            recordCount,
            plaintext,
            compress,
            flushInduced,
        )
}
