package io.sanato.logkit.tools

import io.sanato.logkit.format.Envelope
import io.sanato.logkit.format.FileHeader
import io.sanato.logkit.format.FileHeaderCodec
import io.sanato.logkit.format.FrameCodec
import io.sanato.logkit.format.LogRecordData
import io.sanato.logkit.format.RecordCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * encrypt-in-`:logkit`-format → decrypt-in-this-tool 的真实往返——CI 的
 * `logkit-guard` job 专门跑 `:logkit-decrypt:test` 就是为了让这条往返在每次
 * PR 上都被验证,格式漂移(比如谁改了 HKDF 的 info 字符串却忘了两边一起改)
 * 会在这里第一时间暴露,而不是等到某天需要读一份真实用户日志才发现。
 */
class LogFileDecoderTest {
    private fun buildFile(
        kp: java.security.KeyPair,
        keyId: Int = 1,
        fileSeq: Long = 7L,
        formatVersion: Int = FileHeaderCodec.FORMAT_VERSION,
        recordsPerFrame: Int = 10,
        frameCount: Int = 5,
    ): Pair<ByteArray, List<LogRecordData>> {
        val contentKey = Envelope.generateContentKey()
        val wrappedKey = Envelope.wrap(kp.public, contentKey, keyId, fileSeq)
        val nonceSalt = byteArrayOf(1, 2, 3, 4)
        val header =
            FileHeader(
                formatVersion,
                Envelope.KEM_ID_ECIES_P256.toInt(),
                1,
                1,
                keyId,
                nonceSalt,
                1_700_000_000_000L,
                0L,
                fileSeq,
                1234,
                "main",
                wrappedKey,
                mapOf("sdkVersion" to "1"),
            )
        val headerBytes = FileHeaderCodec.encode(header)

        val allRecords = mutableListOf<LogRecordData>()
        var seq = 0L
        val frames = mutableListOf<ByteArray>()
        repeat(frameCount) { frameIndex ->
            val chunk =
                (0 until recordsPerFrame).map {
                    LogRecordData(
                        seq++,
                        1000L + seq,
                        seq,
                        1,
                        2,
                        "tag",
                        "main",
                        "record $seq",
                    )
                }
            allRecords.addAll(chunk)
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
        return fileBytes to allRecords
    }

    @Test
    fun `decodes a clean file end to end`() {
        val kp = Envelope.generateRecipientKeyPair()
        val (bytes, expected) = buildFile(kp)
        val decoded = LogFileDecoder.decode(bytes, "test.logkit", kp.private)
        assertEquals(expected, decoded.records)
        assertEquals(0, decoded.resyncs)
        assertEquals(0, decoded.authFailures)
        assertEquals(0, decoded.truncatedTailBytes)
    }

    @Test
    fun `reports keyMismatch with the wrong private key`() {
        val kp = Envelope.generateRecipientKeyPair()
        val other = Envelope.generateRecipientKeyPair()
        val (bytes, _) = buildFile(kp)
        val decoded = LogFileDecoder.decode(bytes, "test.logkit", other.private)
        assertTrue(decoded.keyMismatch)
        assertTrue(decoded.records.isEmpty())
    }

    @Test
    fun `reports formatUnsupported for an unknown format version and does not guess`() {
        val kp = Envelope.generateRecipientKeyPair()
        val (bytes, _) = buildFile(kp, formatVersion = 99)
        val decoded = LogFileDecoder.decode(bytes, "test.logkit", kp.private)
        assertTrue(decoded.formatUnsupported)
        assertTrue(decoded.records.isEmpty())
    }

    @Test
    fun `recovers all complete frames and reports a truncated tail`() {
        val kp = Envelope.generateRecipientKeyPair()
        val (bytes, expected) = buildFile(kp, frameCount = 5, recordsPerFrame = 10)
        val truncated = bytes.copyOfRange(0, bytes.size - 7) // 截在最后一帧的 payload 中间
        val decoded = LogFileDecoder.decode(truncated, "test.logkit", kp.private)
        assertEquals(expected.dropLast(10), decoded.records) // 前 4 帧完整恢复
        assertTrue(decoded.truncatedTailBytes > 0)
    }

    @Test
    fun `a single tampered frame is skipped, the rest of the file still decodes`() {
        val kp = Envelope.generateRecipientKeyPair()
        val (bytes, expected) = buildFile(kp, frameCount = 3, recordsPerFrame = 5)
        val tampered = bytes.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1] + 1).toByte() // 翻转最后一帧 payload 的一字节

        val decoded = LogFileDecoder.decode(tampered, "test.logkit", kp.private)
        assertEquals(1, decoded.authFailures)
        assertEquals(expected.dropLast(5), decoded.records) // 前两帧照常恢复
    }

    @Test
    fun `a corrupted frame header triggers a resync to the next valid frame`() {
        val kp = Envelope.generateRecipientKeyPair()
        val (bytes, expected) = buildFile(kp, frameCount = 3, recordsPerFrame = 5)

        // 找到第二帧的起点,翻转它帧头里的一个字节(不是 payload),让 CRC 校验失败。
        val headerLen =
            ((bytes[8].toInt() and 0xFF) shl 24) or ((bytes[9].toInt() and 0xFF) shl 16) or
                ((bytes[10].toInt() and 0xFF) shl 8) or (bytes[11].toInt() and 0xFF)
        val frame0Header = FrameCodec.decodeHeader(bytes, headerLen)
        val frame1Offset = headerLen + FrameCodec.HEADER_LEN + frame0Header.payloadLen
        val corrupted = bytes.copyOf()
        corrupted[frame1Offset + 10] = (corrupted[frame1Offset + 10] + 1).toByte() // 帧头范围内的一个字节

        val decoded = LogFileDecoder.decode(corrupted, "test.logkit", kp.private)
        assertTrue("expected at least one resync", decoded.resyncs > 0)
        // resync 定位到的下一帧是原本的第三帧——第一帧(frame0)照常恢复,第二帧丢失,第三帧恢复。
        assertEquals(expected.take(5) + expected.takeLast(5), decoded.records)
    }
}
