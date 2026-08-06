package io.sanato.logkit.format

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/** 帧明文里的一条记录,纯数据,不含任何 android.* 类型。 */
internal data class LogRecordData(
    val seq: Long,
    val wallMillis: Long,
    val elapsedNanos: Long,
    val threadId: Int,
    val level: Int,
    val tag: String,
    val threadName: String,
    val message: String,
)

internal data class DecodedRecord(
    val record: LogRecordData,
    val bytesConsumed: Int,
)

/**
 * 单条记录的编解码。`recordLen` 前缀让"长度前缀的记录序列"天然支持部分帧恢复——
 * 一份被截断的帧明文仍能按记录边界正确解出前面完整的记录。
 *
 * 布局:recordLen(4) | seq(8) | wallMillis(8) | elapsedNanos(8) | threadId(4) |
 *      level(1) | tagLen(1) | threadNameLen(2) | msgLen(4) | tag‖threadName‖message
 */
internal object RecordCodec {
    private const val FIXED_LEN = 36 // 8+8+8+4+1+1+2+4
    const val MAX_TAG_BYTES = 255
    const val MAX_THREAD_NAME_BYTES = 65535

    fun encode(
        record: LogRecordData,
        maxMessageBytes: Int,
    ): ByteArray {
        val tagBytes = truncateUtf8(record.tag, MAX_TAG_BYTES)
        val threadNameBytes = truncateUtf8(record.threadName, MAX_THREAD_NAME_BYTES)
        val messageBytes = truncateUtf8(record.message, maxMessageBytes)

        val totalLen = FIXED_LEN + tagBytes.size + threadNameBytes.size + messageBytes.size
        val buffer = ByteBuffer.allocate(totalLen + 4).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(totalLen)
        buffer.putLong(record.seq)
        buffer.putLong(record.wallMillis)
        buffer.putLong(record.elapsedNanos)
        buffer.putInt(record.threadId)
        buffer.put(record.level.toByte())
        buffer.put(tagBytes.size.toByte())
        buffer.putShort(threadNameBytes.size.toShort())
        buffer.putInt(messageBytes.size)
        buffer.put(tagBytes)
        buffer.put(threadNameBytes)
        buffer.put(messageBytes)
        return buffer.array()
    }

    /** [offset] 处必须是一条完整记录的起点;调用方负责按 `bytesConsumed` 前进。 */
    fun decode(
        bytes: ByteArray,
        offset: Int,
    ): DecodedRecord {
        if (offset + 4 > bytes.size) throw LogFormatException("truncated record length field")
        val buffer = ByteBuffer.wrap(bytes, offset, bytes.size - offset).order(ByteOrder.BIG_ENDIAN)
        val recordLen = buffer.int
        if (recordLen < FIXED_LEN || offset + 4 + recordLen > bytes.size) {
            throw LogFormatException("record length out of range: $recordLen")
        }
        val seq = buffer.long
        val wallMillis = buffer.long
        val elapsedNanos = buffer.long
        val threadId = buffer.int
        val level = buffer.get().toInt() and 0xFF
        val tagLen = buffer.get().toInt() and 0xFF
        val threadNameLen = buffer.short.toInt() and 0xFFFF
        val msgLen = buffer.int
        val expectedVariable = tagLen + threadNameLen + msgLen
        if (FIXED_LEN + expectedVariable != recordLen) throw LogFormatException("record length mismatch")

        var varOffset = offset + 4 + FIXED_LEN
        val tag = String(bytes, varOffset, tagLen, StandardCharsets.UTF_8)
        varOffset += tagLen
        val threadName = String(bytes, varOffset, threadNameLen, StandardCharsets.UTF_8)
        varOffset += threadNameLen
        val message = String(bytes, varOffset, msgLen, StandardCharsets.UTF_8)

        val record = LogRecordData(seq, wallMillis, elapsedNanos, threadId, level, tag, threadName, message)
        return DecodedRecord(record, bytesConsumed = 4 + recordLen)
    }

    /** 解出一段明文里从 0 开始、恰好 [recordCount] 条的记录。 */
    fun decodeAll(
        bytes: ByteArray,
        recordCount: Int,
    ): List<LogRecordData> {
        val out = ArrayList<LogRecordData>(recordCount)
        var offset = 0
        repeat(recordCount) {
            val decoded = decode(bytes, offset)
            out.add(decoded.record)
            offset += decoded.bytesConsumed
        }
        return out
    }

    private fun truncateUtf8(
        value: String,
        maxBytes: Int,
    ): ByteArray {
        var bytes = value.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size <= maxBytes) return bytes
        // 按 UTF-8 边界截断,不能在多字节字符中间切断——从上限位置往前找到一个
        // 合法的字符边界(高位不是 0x80..0xBF 的续接字节)。
        var cut = maxBytes
        while (cut > 0 && (bytes[cut].toInt() and 0xC0) == 0x80) cut--
        bytes = bytes.copyOfRange(0, cut)
        return bytes
    }
}
