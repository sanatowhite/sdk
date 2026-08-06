package io.sanato.logkit.format

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException
import java.security.SecureRandom

class FrameCodecTest {
    private fun records(n: Int) =
        (0 until n).map { i ->
            LogRecordData(i.toLong(), 1000L + i, 2000L + i, 42, 2, "tag$i", "thread", "message $i " + "x".repeat(30))
        }

    private fun encodeAll(list: List<LogRecordData>): ByteArray =
        list.fold(ByteArray(0)) { acc, r ->
            acc +
                RecordCodec.encode(r, 8192)
        }

    @Test
    fun `seal and open round trip with compression`() {
        val contentKey = Envelope.generateContentKey()
        val nonceSalt = byteArrayOf(9, 9, 9, 9)
        val recs = records(20)
        val plaintext = encodeAll(recs)

        val frame = FrameCodec.seal(contentKey, nonceSalt, 5L, 0L, recs.size, plaintext, compress = true)
        val header = FrameCodec.decodeHeader(frame, 0)
        assertEquals(5L, header.frameIndex)
        assertEquals(recs.size, header.recordCount)

        val opened = FrameCodec.open(contentKey, nonceSalt, header, frame, 0)
        assertArrayEquals(plaintext, opened)
        assertEquals(recs, RecordCodec.decodeAll(opened, header.recordCount))
    }

    @Test
    fun `seal and open round trip without compression`() {
        val contentKey = Envelope.generateContentKey()
        val nonceSalt = byteArrayOf(1, 1, 1, 1)
        val plaintext = RecordCodec.encode(records(1).single(), 8192)
        val frame = FrameCodec.seal(contentKey, nonceSalt, 0L, 0L, 1, plaintext, compress = false)
        val header = FrameCodec.decodeHeader(frame, 0)
        assertArrayEquals(plaintext, FrameCodec.open(contentKey, nonceSalt, header, frame, 0))
    }

    @Test
    fun `header CRC covers exactly bytes 0 through 35`() {
        val contentKey = Envelope.generateContentKey()
        val plaintext = RecordCodec.encode(records(1).single(), 8192)
        val frame = FrameCodec.seal(contentKey, byteArrayOf(0, 0, 0, 0), 0L, 0L, 1, plaintext, compress = false)
        // 翻转帧头内任意一字节(除 CRC 字段本身)都必须让解码失败。
        for (i in 0 until 36) {
            val corrupted = frame.copyOf()
            corrupted[i] = (corrupted[i] + 1).toByte()
            try {
                FrameCodec.decodeHeader(corrupted, 0)
                fail("byte $i should have broken the CRC")
            } catch (e: LogFormatException) {
                // expected
            }
        }
    }

    @Test
    fun `tampered payload fails authentication, not silently`() {
        val contentKey = Envelope.generateContentKey()
        val nonceSalt = byteArrayOf(2, 2, 2, 2)
        val plaintext = RecordCodec.encode(records(1).single(), 8192)
        val frame = FrameCodec.seal(contentKey, nonceSalt, 0L, 0L, 1, plaintext, compress = false)
        frame[frame.size - 1] = (frame[frame.size - 1] + 1).toByte()
        val header = FrameCodec.decodeHeader(frame, 0)
        try {
            FrameCodec.open(contentKey, nonceSalt, header, frame, 0)
            fail("expected GeneralSecurityException")
        } catch (e: GeneralSecurityException) {
            // expected: AEAD 认证失败
        }
    }

    @Test
    fun `nonce never repeats across many frames with the same content key`() {
        val contentKey = Envelope.generateContentKey()
        val nonceSalt = ByteArray(4).also { SecureRandom().nextBytes(it) }
        val seenNonces = HashSet<Long>()
        for (i in 0 until 5000L) {
            val plaintext = RecordCodec.encode(LogRecordData(i, i, i, 1, 1, "t", "th", "m"), 8192)
            val frame = FrameCodec.seal(contentKey, nonceSalt, i, i, 1, plaintext, compress = false)
            val header = FrameCodec.decodeHeader(frame, 0)
            assertTrue(seenNonces.add(header.frameIndex))
        }
    }

    @Test
    fun `a truncated tail is rejected without corrupting earlier frames`() {
        val contentKey = Envelope.generateContentKey()
        val nonceSalt = byteArrayOf(3, 3, 3, 3)
        val frame0 =
            FrameCodec.seal(
                contentKey,
                nonceSalt,
                0L,
                0L,
                1,
                RecordCodec.encode(records(1).single(), 8192),
                compress = false,
            )
        val frame1 =
            FrameCodec.seal(
                contentKey,
                nonceSalt,
                1L,
                1L,
                1,
                RecordCodec.encode(records(1).single(), 8192),
                compress = false,
            )
        val concatenated = frame0 + frame1

        // 截在第二帧中间。
        val truncated = concatenated.copyOfRange(0, frame0.size + 10)
        val header0 = FrameCodec.decodeHeader(truncated, 0)
        assertArrayEquals(
            RecordCodec.encode(records(1).single(), 8192),
            FrameCodec.open(contentKey, nonceSalt, header0, truncated, 0),
        )
        try {
            FrameCodec.decodeHeader(truncated, frame0.size)
            fail("expected truncated header to be rejected")
        } catch (e: LogFormatException) {
            // expected——调用方据此报 truncatedTailBytes,不影响 frame0 已经恢复的事实。
        }
    }
}
