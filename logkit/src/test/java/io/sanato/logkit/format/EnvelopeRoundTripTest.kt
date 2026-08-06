package io.sanato.logkit.format

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException

/**
 * 最重要的一份测试:一份完整的、独立于生产写路径实现的解码器(见下面的
 * [decodeFile]),用测试内生成的密钥对解出一份完整 `.logkit` 文件的全部记录。
 * 共享 helper 里的对称 bug(比如两边都算错同一个 AAD)在这种"两套独立实现
 * 互相校验"的结构下藏不住。
 */
class EnvelopeRoundTripTest {
    @Test
    fun `wrap and unwrap round trips the content key`() {
        val kp = Envelope.generateRecipientKeyPair()
        val contentKey = Envelope.generateContentKey()
        val wrapped = Envelope.wrap(kp.public, contentKey, keyId = 9, fileSeq = 3L)
        assertEquals(Envelope.WRAPPED_KEY_LEN, wrapped.size)
        assertArrayEquals(contentKey, Envelope.unwrap(kp.private, wrapped, keyId = 9, fileSeq = 3L))
    }

    @Test
    fun `unwrap fails when fileSeq binding does not match`() {
        val kp = Envelope.generateRecipientKeyPair()
        val wrapped = Envelope.wrap(kp.public, Envelope.generateContentKey(), keyId = 9, fileSeq = 3L)
        try {
            Envelope.unwrap(kp.private, wrapped, keyId = 9, fileSeq = 4L)
            fail("expected GeneralSecurityException")
        } catch (e: GeneralSecurityException) {
            // AAD 里绑了 fileSeq——换一个就认证失败,不是"解出别的东西"。
        }
    }

    @Test
    fun `unwrap fails when keyId binding does not match`() {
        val kp = Envelope.generateRecipientKeyPair()
        val wrapped = Envelope.wrap(kp.public, Envelope.generateContentKey(), keyId = 9, fileSeq = 3L)
        try {
            Envelope.unwrap(kp.private, wrapped, keyId = 10, fileSeq = 3L)
            fail("expected GeneralSecurityException")
        } catch (e: GeneralSecurityException) {
            // expected
        }
    }

    @Test
    fun `unwrap fails with the wrong private key`() {
        val kp = Envelope.generateRecipientKeyPair()
        val otherKp = Envelope.generateRecipientKeyPair()
        val wrapped = Envelope.wrap(kp.public, Envelope.generateContentKey(), keyId = 1, fileSeq = 0L)
        try {
            Envelope.unwrap(otherKp.private, wrapped, keyId = 1, fileSeq = 0L)
            fail("expected GeneralSecurityException")
        } catch (e: GeneralSecurityException) {
            // expected
        }
    }

    @Test
    fun `selfProbeSymmetric passes on this JVM`() {
        assertTrue(Envelope.selfProbeSymmetric())
    }

    @Test
    fun `end-to-end file round trip via an independently written decoder`() {
        val kp = Envelope.generateRecipientKeyPair()
        val fileSeq = 42L
        val keyId = 7
        val contentKey = Envelope.generateContentKey()
        val wrappedKey = Envelope.wrap(kp.public, contentKey, keyId, fileSeq)
        val nonceSalt = byteArrayOf(5, 6, 7, 8)

        val header =
            FileHeader(
                formatVersion = FileHeaderCodec.FORMAT_VERSION,
                kemId = Envelope.KEM_ID_ECIES_P256.toInt(),
                aeadId = 1,
                compressionId = 1,
                keyId = keyId,
                nonceSalt = nonceSalt,
                createdAtWallMillis = 1_700_000_000_000L,
                createdAtElapsedNanos = 0L,
                fileSeq = fileSeq,
                pid = 999,
                processTag = "main",
                wrappedKey = wrappedKey,
                metadata = mapOf("sdkVersion" to "1"),
            )
        val headerBytes = FileHeaderCodec.encode(header)

        val records =
            (0 until 500).map { i ->
                LogRecordData(
                    i.toLong(),
                    1000L + i,
                    i.toLong(),
                    1,
                    i % 5,
                    "tag",
                    "main",
                    "record number $i",
                )
            }
        val frames = mutableListOf<ByteArray>()
        records.chunked(37).forEachIndexed { frameIndex, chunk ->
            val plaintext = chunk.fold(ByteArray(0)) { acc, r -> acc + RecordCodec.encode(r, 8192) }
            frames.add(
                FrameCodec.seal(
                    contentKey,
                    nonceSalt,
                    frameIndex.toLong(),
                    chunk.first().seq,
                    chunk.size,
                    plaintext,
                    compress = true,
                ),
            )
        }
        val fileBytes = headerBytes + frames.fold(ByteArray(0)) { acc, f -> acc + f }

        val decoded = decodeFile(fileBytes, kp.private)
        assertEquals(records, decoded)
    }

    /**
     * 独立实现的解码器——刻意不复用写路径里 [io.sanato.logkit.LogWriter] 的任何
     * 代码,只依赖 format 包这几个纯函数,这样两边的一致 bug 藏不住。
     */
    private fun decodeFile(
        bytes: ByteArray,
        privateKey: java.security.PrivateKey,
    ): List<LogRecordData> {
        val header = FileHeaderCodec.decode(bytes)
        val contentKey = Envelope.unwrap(privateKey, header.wrappedKey, header.keyId, header.fileSeq)
        val out = mutableListOf<LogRecordData>()
        var offset = headerLengthOf(bytes)
        var expectedFrameIndex = 0L
        while (offset + FrameCodec.HEADER_LEN <= bytes.size) {
            val frameHeader = FrameCodec.decodeHeader(bytes, offset)
            assertEquals(expectedFrameIndex, frameHeader.frameIndex)
            val plaintext = FrameCodec.open(contentKey, header.nonceSalt, frameHeader, bytes, offset)
            out.addAll(RecordCodec.decodeAll(plaintext, frameHeader.recordCount))
            offset += FrameCodec.HEADER_LEN + frameHeader.payloadLen
            expectedFrameIndex++
        }
        return out
    }

    private fun headerLengthOf(bytes: ByteArray): Int {
        // headerLength 是文件头第 8..11 字节,大端 uint32——这里不复用 FileHeaderCodec
        // 内部实现,直接按格式文档手算,进一步保证解码器与写路径互相独立。
        return ((bytes[8].toInt() and 0xFF) shl 24) or
            ((bytes[9].toInt() and 0xFF) shl 16) or
            ((bytes[10].toInt() and 0xFF) shl 8) or
            (bytes[11].toInt() and 0xFF)
    }
}
