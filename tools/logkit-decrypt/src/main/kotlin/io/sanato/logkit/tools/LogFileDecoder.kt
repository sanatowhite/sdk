package io.sanato.logkit.tools

import io.sanato.logkit.format.Envelope
import io.sanato.logkit.format.FileHeaderCodec
import io.sanato.logkit.format.FrameCodec
import io.sanato.logkit.format.LogFormatException
import io.sanato.logkit.format.LogRecordData
import io.sanato.logkit.format.RecordCodec
import java.security.GeneralSecurityException
import java.security.PrivateKey

internal data class DecodedFile(
    val sourceFileName: String,
    val fileSeq: Long,
    val processTag: String,
    val metadata: Map<String, String>,
    val records: List<LogRecordData>,
    val resyncs: Int,
    val authFailures: Int,
    val truncatedTailBytes: Int,
    val keyMismatch: Boolean,
    val formatUnsupported: Boolean,
    val headerUnusable: Boolean,
)

/**
 * 与写路径(`:logkit` 的 `LogWriter`/`FrameBuilder`)完全独立的读方实现——只依赖
 * `io.sanato.logkit.format` 里的纯函数。这是 [io.sanato.logkit.format.EnvelopeRoundTripTest]
 * 那条"两套独立实现互相校验"原则在真实解密工具里的落地。
 *
 * 恢复策略与格式文档("Reader algorithm")逐条对应:
 *  - 头不可用 ⇒ 整个文件不可用,不做部分头恢复。
 *  - keyId 不匹配的 wrappedKey ⇒ 密钥不对,不是格式不对。
 *  - 单帧认证失败(GCM tag 不过)⇒ 只丢这一帧,继续读后面的帧。
 *  - 帧头 CRC 不过 ⇒ 逐字节向后扫描,找下一个 CRC 能过的 "LKFR",不是直接判定整份文件损坏。
 *  - 长度字段永远先过 CRC 才敢用来 seek/分配。
 */
internal object LogFileDecoder {
    private const val MAX_INFLATE_BYTES = FrameCodec.MAX_PLAINTEXT_BYTES // 攻击面上界,见格式文档 §2.6。

    fun decode(
        bytes: ByteArray,
        sourceFileName: String,
        privateKey: PrivateKey,
    ): DecodedFile {
        val header =
            try {
                FileHeaderCodec.decode(bytes)
            } catch (e: LogFormatException) {
                return DecodedFile(
                    sourceFileName,
                    -1,
                    "",
                    emptyMap(),
                    emptyList(),
                    0,
                    0,
                    bytes.size,
                    false,
                    false,
                    true,
                )
            }

        if (header.formatVersion != FileHeaderCodec.FORMAT_VERSION) {
            return DecodedFile(
                sourceFileName,
                header.fileSeq,
                header.processTag,
                header.metadata,
                emptyList(),
                0,
                0,
                0,
                false,
                true,
                false,
            )
        }

        val contentKey =
            try {
                Envelope.unwrap(privateKey, header.wrappedKey, header.keyId, header.fileSeq)
            } catch (e: GeneralSecurityException) {
                return DecodedFile(
                    sourceFileName,
                    header.fileSeq,
                    header.processTag,
                    header.metadata,
                    emptyList(),
                    0,
                    0,
                    0,
                    true,
                    false,
                    false,
                )
            }

        val records = mutableListOf<LogRecordData>()
        var resyncs = 0
        var authFailures = 0
        var truncatedTailBytes = 0
        var offset = headerLengthOf(bytes)

        while (true) {
            val remaining = bytes.size - offset
            if (remaining < FrameCodec.HEADER_LEN) {
                truncatedTailBytes = remaining
                break
            }
            val frameHeader =
                try {
                    FrameCodec.decodeHeader(bytes, offset)
                } catch (e: LogFormatException) {
                    val resyncOffset = findNextValidFrame(bytes, offset + 1)
                    if (resyncOffset == null) {
                        truncatedTailBytes = remaining
                        break
                    }
                    resyncs++
                    offset = resyncOffset
                    continue
                }
            if (offset + FrameCodec.HEADER_LEN + frameHeader.payloadLen > bytes.size) {
                truncatedTailBytes = remaining
                break
            }
            if (frameHeader.plaintextLen > MAX_INFLATE_BYTES) {
                // 公钥随 APK 分发,任何人都能造出语法合法、认证通过的文件——
                // 这个上界拒绝的是"合法但离谱"的输入,不是等 inflate 炸内存才发现。
                authFailures++
                offset += FrameCodec.HEADER_LEN + frameHeader.payloadLen
                continue
            }
            try {
                val plaintext = FrameCodec.open(contentKey, header.nonceSalt, frameHeader, bytes, offset)
                records.addAll(RecordCodec.decodeAll(plaintext, frameHeader.recordCount))
            } catch (e: GeneralSecurityException) {
                authFailures++
            } catch (e: LogFormatException) {
                authFailures++
            }
            offset += FrameCodec.HEADER_LEN + frameHeader.payloadLen
        }

        return DecodedFile(
            sourceFileName,
            header.fileSeq,
            header.processTag,
            header.metadata,
            records,
            resyncs,
            authFailures,
            truncatedTailBytes,
            keyMismatch = false,
            formatUnsupported = false,
            headerUnusable = false,
        )
    }

    private fun findNextValidFrame(
        bytes: ByteArray,
        from: Int,
    ): Int? {
        var candidate = from
        while (candidate + FrameCodec.HEADER_LEN <= bytes.size) {
            try {
                FrameCodec.decodeHeader(bytes, candidate)
                return candidate
            } catch (e: LogFormatException) {
                candidate++
            }
        }
        return null
    }

    private fun headerLengthOf(bytes: ByteArray): Int =
        ((bytes[8].toInt() and 0xFF) shl 24) or
            ((bytes[9].toInt() and 0xFF) shl 16) or
            ((bytes[10].toInt() and 0xFF) shl 8) or
            (bytes[11].toInt() and 0xFF)
}
