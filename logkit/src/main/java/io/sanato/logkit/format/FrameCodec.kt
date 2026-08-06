package io.sanato.logkit.format

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 一帧 = 40 字节头 + payload(deflate 后再 AES-256-GCM 加密的记录)。
 *
 * 三条不可动摇的不变量(详见 logkit/README.md):
 *  1. 每帧独立 deflate + 独立 GCM——[Deflater] 每帧 `reset()`,跨帧共享一个
 *     deflate 流会让截尾摧毁文件其余部分。
 *  2. nonce = nonceSalt(文件级,4B) ‖ frameIndex(帧级,8B)——同一内容密钥下
 *     frameIndex 严格递增,不可能重复,前提是"永不追加已有文件"(见
 *     [io.sanato.logkit.LogWriter])。
 *  3. 未经 headerCrc32 校验的 `payloadLen` 不可信,不能拿去 seek/分配。
 */
internal data class FrameHeader(
    val frameFlags: Int,
    val frameIndex: Long,
    val firstRecordSeq: Long,
    val recordCount: Int,
    val plaintextLen: Int,
    val payloadLen: Int,
)

internal object FrameCodec {
    const val MAGIC = 0x4C4B4652 // "LKFR"
    const val HEADER_LEN = 40
    const val FLAG_DEFLATED = 1
    const val FLAG_FLUSH_INDUCED = 2

    /** 单帧密文上界——真实设备内存与 4MiB 攻击面上界的折中,见 [maxPlaintextBytes]。 */
    const val MAX_PLAINTEXT_BYTES = 4 * 1024 * 1024

    fun encodeHeader(header: FrameHeader): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_LEN).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.put(header.frameFlags.toByte())
        buffer.put(0) // reserved
        buffer.put(0)
        buffer.put(0)
        buffer.putLong(header.frameIndex)
        buffer.putLong(header.firstRecordSeq)
        buffer.putInt(header.recordCount)
        buffer.putInt(header.plaintextLen)
        buffer.putInt(header.payloadLen)
        val bytes = buffer.array()
        val crc = CRC32().apply { update(bytes, 0, 36) }.value
        ByteBuffer.wrap(bytes, 36, 4).order(ByteOrder.BIG_ENDIAN).putInt(crc.toInt())
        return bytes
    }

    /** 严格要求 [offset] 处就是一个合法帧头。resync 扫描由调用方(读方)负责。 */
    fun decodeHeader(
        bytes: ByteArray,
        offset: Int,
    ): FrameHeader {
        if (offset + HEADER_LEN > bytes.size) throw LogFormatException("truncated frame header")
        val buffer = ByteBuffer.wrap(bytes, offset, HEADER_LEN).order(ByteOrder.BIG_ENDIAN)
        val magic = buffer.int
        if (magic != MAGIC) throw LogFormatException("bad frame magic at offset $offset")
        val flags = buffer.get().toInt() and 0xFF
        buffer.get()
        buffer.get()
        buffer.get() // reserved
        val frameIndex = buffer.long
        val firstRecordSeq = buffer.long
        val recordCount = buffer.int
        val plaintextLen = buffer.int
        val payloadLen = buffer.int
        val storedCrc = buffer.int

        val expectedCrc = CRC32().apply { update(bytes, offset, 36) }.value
        if (storedCrc.toLong() and 0xFFFFFFFFL != expectedCrc) {
            throw LogFormatException("frame header CRC mismatch at offset $offset")
        }
        if (payloadLen < 0 || payloadLen > MAX_PLAINTEXT_BYTES + 64) {
            throw LogFormatException("payloadLen out of range: $payloadLen")
        }
        if (plaintextLen < 0 || plaintextLen > MAX_PLAINTEXT_BYTES) {
            throw LogFormatException("plaintextLen out of range: $plaintextLen")
        }
        return FrameHeader(flags, frameIndex, firstRecordSeq, recordCount, plaintextLen, payloadLen)
    }

    /**
     * 压缩(可选) + AES-256-GCM 加密 + 拼头,返回完整帧字节。
     * `nonce = nonceSalt(4) ‖ frameIndex(8)`;`aad = 帧头[0,36)`。
     */
    fun seal(
        contentKey: ByteArray,
        nonceSalt: ByteArray,
        frameIndex: Long,
        firstRecordSeq: Long,
        recordCount: Int,
        plaintext: ByteArray,
        compress: Boolean,
        flushInduced: Boolean = false,
    ): ByteArray {
        require(nonceSalt.size == 4) { "nonceSalt must be 4 bytes" }
        val toEncrypt =
            if (compress) {
                deflate(plaintext)
            } else {
                plaintext
            }

        var flags = if (compress) FLAG_DEFLATED else 0
        if (flushInduced) flags = flags or FLAG_FLUSH_INDUCED

        // GCM 会在密文后追加 16 字节 tag,所以 payloadLen 在加密前就能算出来
        // (toEncrypt.size + 16)——头一次编码即为最终头,AAD = 头[0,36)。
        val payloadLen = toEncrypt.size + 16
        val header =
            encodeHeader(FrameHeader(flags, frameIndex, firstRecordSeq, recordCount, plaintext.size, payloadLen))
        val nonce = nonceSalt + longToBytes(frameIndex)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(contentKey, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header, 0, 36)
        val sealed = cipher.doFinal(toEncrypt)

        return header + sealed
    }

    /** 解密并(如有需要)解压一帧,不含 resync——由调用方按 [decodeHeader] 定位。 */
    fun open(
        contentKey: ByteArray,
        nonceSalt: ByteArray,
        header: FrameHeader,
        frameBytes: ByteArray,
        headerOffset: Int,
    ): ByteArray {
        val payloadStart = headerOffset + HEADER_LEN
        if (payloadStart + header.payloadLen > frameBytes.size) throw LogFormatException("payload exceeds buffer")
        val nonce = nonceSalt + longToBytes(header.frameIndex)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(contentKey, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(frameBytes, headerOffset, 36)
        // GeneralSecurityException(通常是 AEADBadTagException)在此抛出——认证失败,
        // 调用方按"这一帧 authFailed,跳过继续下一帧"处理,不是致命错误。
        val decrypted = cipher.doFinal(frameBytes, payloadStart, header.payloadLen)

        val plaintext =
            if (header.frameFlags and FLAG_DEFLATED != 0) {
                inflate(decrypted, header.plaintextLen)
            } else {
                decrypted
            }
        if (plaintext.size != header.plaintextLen) throw LogFormatException("inflated size mismatch")
        return plaintext
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true) // raw deflate,无第三方
        deflater.setInput(data)
        deflater.finish()
        val out = java.io.ByteArrayOutputStream(maxOf(64, data.size / 2))
        val buf = ByteArray(8192)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    /** [expectedSize] 是硬上界与断言目标——解密工具面对的是可被任意构造的输入。 */
    private fun inflate(
        data: ByteArray,
        expectedSize: Int,
    ): ByteArray {
        if (expectedSize > MAX_PLAINTEXT_BYTES) throw LogFormatException("plaintextLen exceeds hard bound")
        val inflater = Inflater(true)
        inflater.setInput(data)
        val out = ByteArray(expectedSize)
        var totalOut = 0
        try {
            while (totalOut < expectedSize && !inflater.finished()) {
                val n = inflater.inflate(out, totalOut, expectedSize - totalOut)
                if (n == 0 && inflater.needsInput()) break
                totalOut += n
            }
        } catch (e: java.util.zip.DataFormatException) {
            throw LogFormatException("deflate stream corrupt: ${e.message}")
        } finally {
            inflater.end()
        }
        if (totalOut != expectedSize) throw LogFormatException("inflate size mismatch: $totalOut != $expectedSize")
        return out
    }

    private fun longToBytes(value: Long): ByteArray = ByteArray(8) { i -> (value ushr ((7 - i) * 8)).toByte() }
}
