package io.sanato.logkit

import io.sanato.logkit.format.Envelope
import io.sanato.logkit.format.FileHeaderCodec
import io.sanato.logkit.format.FrameCodec
import io.sanato.logkit.format.LogRecordData
import io.sanato.logkit.format.RecordCodec
import java.io.File
import java.security.PrivateKey

/**
 * 测试专用、与生产写路径完全独立的解码器(和 [io.sanato.logkit.format.EnvelopeRoundTripTest]
 * 里那份是同一个思路,复制到这里是因为它是 `internal` 类型,不能跨模块 test
 * source set 共享)。用来验证 [LogKitCore]/[LogKit] 层面写出来的文件真的
 * 可以被独立解密,而不是"写路径自己骗自己"。
 */
internal object TestLogFileDecoder {
    fun decodeAllRecords(
        files: List<File>,
        privateKey: PrivateKey,
    ): List<LogRecordData> =
        files
            .sortedBy {
                LogFileNaming.parse(it.name)?.fileSeq ?: -1L
            }.flatMap { decodeFile(it.readBytes(), privateKey) }

    private fun decodeFile(
        bytes: ByteArray,
        privateKey: PrivateKey,
    ): List<LogRecordData> {
        val header = FileHeaderCodec.decode(bytes)
        val contentKey = Envelope.unwrap(privateKey, header.wrappedKey, header.keyId, header.fileSeq)
        val out = mutableListOf<LogRecordData>()
        var offset = headerLengthOf(bytes)
        while (offset + FrameCodec.HEADER_LEN <= bytes.size) {
            val frameHeader = FrameCodec.decodeHeader(bytes, offset)
            val plaintext = FrameCodec.open(contentKey, header.nonceSalt, frameHeader, bytes, offset)
            out.addAll(RecordCodec.decodeAll(plaintext, frameHeader.recordCount))
            offset += FrameCodec.HEADER_LEN + frameHeader.payloadLen
        }
        return out
    }

    private fun headerLengthOf(bytes: ByteArray): Int =
        ((bytes[8].toInt() and 0xFF) shl 24) or
            ((bytes[9].toInt() and 0xFF) shl 16) or
            ((bytes[10].toInt() and 0xFF) shl 8) or
            (bytes[11].toInt() and 0xFF)
}
