package io.sanato.logkit.format

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordCodecTest {
    @Test
    fun `round trips including emoji and multibyte text`() {
        val record = LogRecordData(1L, 2L, 3L, 100, 2, "tag", "main", "hello 😀 world 中文")
        val encoded = RecordCodec.encode(record, maxMessageBytes = 8192)
        val decoded = RecordCodec.decode(encoded, 0)
        assertEquals(record, decoded.record)
        assertEquals(encoded.size, decoded.bytesConsumed)
    }

    @Test
    fun `handles empty tag and message`() {
        val record = LogRecordData(0L, 0L, 0L, 0, 0, "", "t", "")
        val encoded = RecordCodec.encode(record, maxMessageBytes = 100)
        val decoded = RecordCodec.decode(encoded, 0)
        assertEquals(record, decoded.record)
    }

    @Test
    fun `truncates an over-long message at a UTF-8 boundary`() {
        val message = "x".repeat(10) + "😀".repeat(10) // 10 ASCII + 10 emoji (4 bytes each)
        val record = LogRecordData(0L, 0L, 0L, 0, 0, "t", "th", message)
        val encoded = RecordCodec.encode(record, maxMessageBytes = 13) // 落在 emoji 中间
        val decoded = RecordCodec.decode(encoded, 0)
        // 截断后的字节数组本身必须是合法 UTF-8(不能抛 MalformedInputException)。
        val messageBytes = decoded.record.message.toByteArray(Charsets.UTF_8)
        assertEquals(String(messageBytes, Charsets.UTF_8), decoded.record.message)
    }

    @Test
    fun `decodeAll decodes a concatenation of several records in order`() {
        val records =
            (0 until 5).map {
                LogRecordData(
                    it.toLong(),
                    it.toLong(),
                    it.toLong(),
                    it,
                    1,
                    "t$it",
                    "th",
                    "m$it",
                )
            }
        val bytes = records.fold(ByteArray(0)) { acc, r -> acc + RecordCodec.encode(r, 100) }
        val decoded = RecordCodec.decodeAll(bytes, records.size)
        assertEquals(records, decoded)
    }
}
