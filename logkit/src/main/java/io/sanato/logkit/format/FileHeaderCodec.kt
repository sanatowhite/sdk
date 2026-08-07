package io.sanato.logkit.format

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/**
 * `.logkit` 文件头:64 字节固定部分 + 变长尾(processTag / wrappedKey / metadata),
 * 对齐到 16 的倍数。完整字节布局见 logkit/README.md 和 docs/adr/0010。
 *
 * 头是明文——不含密钥,只含"用哪个密钥包裹了内容密钥"。这样即使没有私钥,
 * 支持工程师也能看 `metadata` 分诊(⚠️ 所以 metadata 绝不能塞 PII)。
 */
internal data class FileHeader(
    val formatVersion: Int,
    val kemId: Int,
    val aeadId: Int,
    val compressionId: Int,
    val keyId: Int,
    val nonceSalt: ByteArray,
    val createdAtWallMillis: Long,
    val createdAtElapsedNanos: Long,
    val fileSeq: Long,
    val pid: Int,
    val processTag: String,
    val wrappedKey: ByteArray,
    val metadata: Map<String, String>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileHeader) return false
        return formatVersion == other.formatVersion &&
            kemId == other.kemId &&
            aeadId == other.aeadId &&
            compressionId == other.compressionId &&
            keyId == other.keyId &&
            nonceSalt.contentEquals(other.nonceSalt) &&
            createdAtWallMillis == other.createdAtWallMillis &&
            createdAtElapsedNanos == other.createdAtElapsedNanos &&
            fileSeq == other.fileSeq &&
            pid == other.pid &&
            processTag == other.processTag &&
            wrappedKey.contentEquals(other.wrappedKey) &&
            metadata == other.metadata
    }

    override fun hashCode(): Int {
        var result = formatVersion
        result = 31 * result + fileSeq.hashCode()
        result = 31 * result + processTag.hashCode()
        return result
    }
}

internal object FileHeaderCodec {
    const val MAGIC = 0x4C4B4631 // "LKF1"
    const val FORMAT_VERSION = 1
    private const val FIXED_LEN = 64
    private const val ALIGNMENT = 16

    fun encode(header: FileHeader): ByteArray {
        val processTagBytes = header.processTag.toByteArray(StandardCharsets.UTF_8)
        val metadataBytes = encodeMetadata(header.metadata)
        require(header.nonceSalt.size == 4) { "nonceSalt must be 4 bytes" }
        require(processTagBytes.size <= 0xFFFF && header.wrappedKey.size <= 0xFFFF && metadataBytes.size <= 0xFFFF) {
            "tail section too large"
        }

        val rawTailLen = processTagBytes.size + header.wrappedKey.size + metadataBytes.size
        val headerLength = align(FIXED_LEN + rawTailLen, ALIGNMENT)

        val buffer = ByteBuffer.allocate(headerLength).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.put(header.formatVersion.toByte())
        buffer.put(header.kemId.toByte())
        buffer.put(header.aeadId.toByte())
        buffer.put(header.compressionId.toByte())
        buffer.putInt(headerLength)
        buffer.putInt(header.keyId)
        buffer.put(header.nonceSalt)
        buffer.putLong(header.createdAtWallMillis)
        buffer.putLong(header.createdAtElapsedNanos)
        buffer.putLong(header.fileSeq)
        buffer.putInt(header.pid)
        buffer.putShort(processTagBytes.size.toShort())
        buffer.putShort(header.wrappedKey.size.toShort())
        buffer.putShort(metadataBytes.size.toShort())
        buffer.putShort(0) // reserved0
        buffer.putInt(0) // headerCrc32 placeholder, patched below
        buffer.putInt(0) // reserved1

        check(buffer.position() == FIXED_LEN) { "fixed header layout drifted: ${buffer.position()}" }
        buffer.put(processTagBytes)
        buffer.put(header.wrappedKey)
        buffer.put(metadataBytes)
        // 剩余字节保持零填充——ByteBuffer.allocate 已经是零初始化。

        val bytes = buffer.array()
        val crc = crcOverHeader(bytes, headerLength)
        ByteBuffer.wrap(bytes, 56, 4).order(ByteOrder.BIG_ENDIAN).putInt(crc.toInt())
        return bytes
    }

    /**
     * 只解析并校验 CRC,不做业务判断(格式版本是否支持等留给调用方)。
     * 抛 [LogFormatException] 意味着这个文件从头就不可用——不做部分头恢复,
     * 头本来就是一次 write() 写下的几百字节。
     */
    fun decode(bytes: ByteArray): FileHeader {
        if (bytes.size < FIXED_LEN) throw LogFormatException("header shorter than fixed part")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val magic = buffer.int
        if (magic != MAGIC) throw LogFormatException("bad magic: ${Integer.toHexString(magic)}")
        val formatVersion = buffer.get().toInt() and 0xFF
        val kemId = buffer.get().toInt() and 0xFF
        val aeadId = buffer.get().toInt() and 0xFF
        val compressionId = buffer.get().toInt() and 0xFF
        val headerLength = buffer.int
        if (headerLength < FIXED_LEN || headerLength > bytes.size) {
            throw LogFormatException("headerLength out of range: $headerLength")
        }
        val keyId = buffer.int
        val nonceSalt = ByteArray(4).also { buffer.get(it) }
        val createdAtWallMillis = buffer.long
        val createdAtElapsedNanos = buffer.long
        val fileSeq = buffer.long
        val pid = buffer.int
        val processTagLen = buffer.short.toInt() and 0xFFFF
        val wrappedKeyLen = buffer.short.toInt() and 0xFFFF
        val metaLen = buffer.short.toInt() and 0xFFFF
        buffer.short // reserved0
        val storedCrc = buffer.int
        buffer.int // reserved1
        check(buffer.position() == FIXED_LEN)

        val expectedCrc = crcOverHeader(bytes, headerLength)
        if (storedCrc.toLong() and 0xFFFFFFFFL != expectedCrc) {
            throw LogFormatException("header CRC mismatch")
        }

        val tailStart = FIXED_LEN
        if (tailStart + processTagLen + wrappedKeyLen + metaLen > headerLength) {
            throw LogFormatException("tail lengths exceed headerLength")
        }
        var offset = tailStart
        val processTag = String(bytes, offset, processTagLen, StandardCharsets.UTF_8)
        offset += processTagLen
        val wrappedKey = bytes.copyOfRange(offset, offset + wrappedKeyLen)
        offset += wrappedKeyLen
        val metadataBytes = bytes.copyOfRange(offset, offset + metaLen)
        val metadata = decodeMetadata(metadataBytes)

        return FileHeader(
            formatVersion = formatVersion,
            kemId = kemId,
            aeadId = aeadId,
            compressionId = compressionId,
            keyId = keyId,
            nonceSalt = nonceSalt,
            createdAtWallMillis = createdAtWallMillis,
            createdAtElapsedNanos = createdAtElapsedNanos,
            fileSeq = fileSeq,
            pid = pid,
            processTag = processTag,
            wrappedKey = wrappedKey,
            metadata = metadata,
        )
    }

    /** CRC 覆盖 [0,56) ∪ [64,headerLength)——跳过 CRC 字段自身与 reserved1。 */
    private fun crcOverHeader(
        bytes: ByteArray,
        headerLength: Int,
    ): Long {
        val crc = CRC32()
        crc.update(bytes, 0, 56)
        crc.update(bytes, 64, headerLength - 64)
        return crc.value
    }

    private fun encodeMetadata(metadata: Map<String, String>): ByteArray =
        metadata.entries.joinToString(separator = "") { (k, v) -> "$k=$v\n" }.toByteArray(StandardCharsets.UTF_8)

    private fun decodeMetadata(bytes: ByteArray): Map<String, String> {
        if (bytes.isEmpty()) return emptyMap()
        val text = String(bytes, StandardCharsets.UTF_8)
        return text
            .split("\n")
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx < 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }.toMap()
    }

    private fun align(
        value: Int,
        alignment: Int,
    ): Int = ((value + alignment - 1) / alignment) * alignment
}
