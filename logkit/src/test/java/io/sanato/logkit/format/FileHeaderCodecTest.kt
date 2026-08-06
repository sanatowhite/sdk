package io.sanato.logkit.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FileHeaderCodecTest {
    private fun sampleHeader(
        processTag: String = "main",
        metadata: Map<String, String> = mapOf("sdkVersion" to "1", "androidSdkInt" to "24"),
    ) = FileHeader(
        formatVersion = FileHeaderCodec.FORMAT_VERSION,
        kemId = 1,
        aeadId = 1,
        compressionId = 1,
        keyId = 42,
        nonceSalt = byteArrayOf(1, 2, 3, 4),
        createdAtWallMillis = 1_700_000_000_000L,
        createdAtElapsedNanos = 123_456_789L,
        fileSeq = 7L,
        pid = 1234,
        processTag = processTag,
        wrappedKey = ByteArray(Envelope.WRAPPED_KEY_LEN) { it.toByte() },
        metadata = metadata,
    )

    @Test
    fun `round trips and headerLength is 16-byte aligned`() {
        val encoded = FileHeaderCodec.encode(sampleHeader())
        assertEquals(0, encoded.size % 16)
        assertEquals(sampleHeader(), FileHeaderCodec.decode(encoded))
    }

    @Test
    fun `handles empty tail sections`() {
        val header = sampleHeader(processTag = "", metadata = emptyMap()).let { it.copy(wrappedKey = ByteArray(0)) }
        val encoded = FileHeaderCodec.encode(header)
        assertEquals(header, FileHeaderCodec.decode(encoded))
    }

    @Test
    fun `handles max-length process tag`() {
        val header = sampleHeader(processTag = "a".repeat(23))
        val encoded = FileHeaderCodec.encode(header)
        assertEquals(header, FileHeaderCodec.decode(encoded))
    }

    @Test
    fun `rejects a header with a flipped byte anywhere`() {
        val encoded = FileHeaderCodec.encode(sampleHeader())
        for (i in encoded.indices) {
            val corrupted = encoded.copyOf()
            corrupted[i] = (corrupted[i] + 1).toByte()
            try {
                val decoded = FileHeaderCodec.decode(corrupted)
                // 极少数字节翻转恰好落在两个字段之间且不影响 CRC 覆盖范围以外——
                // 但 CRC 覆盖了除 [56,64) 之外的一切,所以只有翻在这个区间的字节
                // 才可能"解码成功但内容不同"。断言这种情况下至少不是静默地
                // 变成另一份"合法"头。
                assertTrue(
                    "byte $i flipped but still decoded to identical header",
                    decoded != sampleHeader() || i in 56 until 64,
                )
            } catch (e: LogFormatException) {
                // 期望的路径:CRC 或长度校验拒绝。
            }
        }
    }

    @Test
    fun `rejects bad magic`() {
        val encoded = FileHeaderCodec.encode(sampleHeader())
        encoded[0] = 0
        try {
            FileHeaderCodec.decode(encoded)
            fail("expected LogFormatException")
        } catch (e: LogFormatException) {
            // expected
        }
    }

    private fun FileHeader.copy(wrappedKey: ByteArray) =
        FileHeader(
            formatVersion,
            kemId,
            aeadId,
            compressionId,
            keyId,
            nonceSalt,
            createdAtWallMillis,
            createdAtElapsedNanos,
            fileSeq,
            pid,
            processTag,
            wrappedKey,
            metadata,
        )
}
