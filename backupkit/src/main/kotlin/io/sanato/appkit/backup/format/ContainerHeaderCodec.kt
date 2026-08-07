package io.sanato.appkit.backup.format

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * SBK1 固定头（48 字节，大端）+ 变长尾的编解码。字段布局是 normative 的，见 README：
 *
 * ```
 * Off  Len  字段
 * 0    4    magic ("SBK1")
 * 4    1    formatVersion
 * 5    1    cipherId
 * 6    1    macId
 * 7    1    kdfId
 * 8    2    headerLength（头总长，16 对齐；读方必须按此跳转，不得自行推算）
 * 10   1    contentType
 * 11   1    payloadProfile
 * 12   4    kdfIterations
 * 16   1    saltLength
 * 17   1    ivLength
 * 18   1    macLength
 * 19   1    keyLength
 * 20   8    createdAtWallMillis（仅展示，不参与任何判定）
 * 28   2    producerLength
 * 30   2    metadataLength（当前恒为 0，保留给未来扩展）
 * 32   4    headerCrc32（覆盖 [0,32) ∪ [36,headerLength)，未认证，只挡意外损坏）
 * 36   4    reserved0（写 0，读方忽略非零）
 * 40   8    plaintextLength（未知写 -1）
 * ```
 *
 * 变长尾（offset 48 起）：salt ‖ iv ‖ producer(UTF-8) ‖ 零补齐到 headerLength。
 */
internal object ContainerHeaderCodec {
    fun encode(
        cipherId: Int,
        macId: Int,
        kdfId: Int,
        contentType: Int,
        payloadProfile: Int,
        kdfIterations: Int,
        saltLength: Int,
        ivLength: Int,
        macLength: Int,
        keyLength: Int,
        createdAtWallMillis: Long,
        plaintextLength: Long,
        producer: String,
        salt: ByteArray,
        iv: ByteArray,
    ): ByteArray {
        val producerBytes = producer.toByteArray(Charsets.UTF_8)
        val tailSize = saltLength + ivLength + producerBytes.size
        val unalignedLength = BackupAlgorithms.HEADER_FIXED_SIZE + tailSize
        val headerLength =
            ((unalignedLength + BackupAlgorithms.HEADER_ALIGNMENT - 1) / BackupAlgorithms.HEADER_ALIGNMENT) *
                BackupAlgorithms.HEADER_ALIGNMENT

        val buf = ByteBuffer.allocate(headerLength).order(ByteOrder.BIG_ENDIAN)
        buf.put(BackupAlgorithms.MAGIC.toByteArray(Charsets.US_ASCII))
        buf.put(BackupAlgorithms.FORMAT_VERSION.toByte())
        buf.put(cipherId.toByte())
        buf.put(macId.toByte())
        buf.put(kdfId.toByte())
        buf.putShort(headerLength.toShort())
        buf.put(contentType.toByte())
        buf.put(payloadProfile.toByte())
        buf.putInt(kdfIterations)
        buf.put(saltLength.toByte())
        buf.put(ivLength.toByte())
        buf.put(macLength.toByte())
        buf.put(keyLength.toByte())
        buf.putLong(createdAtWallMillis)
        buf.putShort(producerBytes.size.toShort())
        buf.putShort(0) // metadataLength，恒 0
        val crcPlaceholderPos = buf.position()
        buf.putInt(0) // headerCrc32 占位，最后回填
        buf.putInt(0) // reserved0
        buf.putLong(plaintextLength)
        buf.put(salt)
        buf.put(iv)
        buf.put(producerBytes)
        // 其余字节保持 ByteBuffer 默认的 0 值，即"零补齐到 headerLength"。

        val crc = CRC32()
        crc.update(buf.array(), 0, 32)
        crc.update(buf.array(), 36, headerLength - 36)
        buf.putInt(crcPlaceholderPos, crc.value.toInt())

        return buf.array()
    }

    /** 从文件开头读取并校验固定头 + 变长尾。抛 [BackupFormatException] 系列异常。 */
    fun decode(
        file: RandomAccessFile,
        path: String,
    ): ContainerHeader {
        if (file.length() < BackupAlgorithms.HEADER_FIXED_SIZE) {
            throw BackupFormatException.Truncated(path)
        }
        val fixed = ByteArray(BackupAlgorithms.HEADER_FIXED_SIZE)
        file.seek(0)
        file.readFully(fixed)
        val fixedBuf = ByteBuffer.wrap(fixed).order(ByteOrder.BIG_ENDIAN)

        val magic = ByteArray(4).also { fixedBuf.get(it) }
        if (String(magic, Charsets.US_ASCII) != BackupAlgorithms.MAGIC) {
            throw BackupFormatException.NotABackupFile(path)
        }
        val formatVersion = fixedBuf.get().toInt() and 0xFF
        if (formatVersion != BackupAlgorithms.FORMAT_VERSION) {
            throw BackupFormatException.UnsupportedFormatVersion(
                formatVersion,
                BackupAlgorithms.FORMAT_VERSION..BackupAlgorithms.FORMAT_VERSION,
            )
        }
        val cipherId = fixedBuf.get().toInt() and 0xFF
        val macId = fixedBuf.get().toInt() and 0xFF
        val kdfId = fixedBuf.get().toInt() and 0xFF
        val headerLength = fixedBuf.short.toInt() and 0xFFFF
        val contentType = fixedBuf.get().toInt() and 0xFF
        val payloadProfile = fixedBuf.get().toInt() and 0xFF
        val kdfIterations = fixedBuf.int
        val saltLength = fixedBuf.get().toInt() and 0xFF
        val ivLength = fixedBuf.get().toInt() and 0xFF
        val macLength = fixedBuf.get().toInt() and 0xFF
        val keyLength = fixedBuf.get().toInt() and 0xFF
        val createdAtWallMillis = fixedBuf.long
        val producerLength = fixedBuf.short.toInt() and 0xFFFF

        @Suppress("UNUSED_VARIABLE")
        val metadataLength = fixedBuf.short.toInt() and 0xFFFF
        val headerCrc32 = fixedBuf.int

        @Suppress("UNUSED_VARIABLE")
        val reserved0 = fixedBuf.int
        val plaintextLength = fixedBuf.long

        if (file.length() < headerLength) {
            throw BackupFormatException.Truncated(path)
        }

        // cipherId/macId/kdfId 未知即报错，即使 formatVersion 已知——新增算法不需要 bump
        // formatVersion，但读方必须能明确认出"这个算法我不认识"。
        if (cipherId !=
            BackupAlgorithms.CIPHER_AES256_CTR
        ) {
            throw BackupFormatException.UnsupportedAlgorithm("cipherId", cipherId)
        }
        if (macId != BackupAlgorithms.MAC_HMAC_SHA256) throw BackupFormatException.UnsupportedAlgorithm("macId", macId)
        if (kdfId !=
            BackupAlgorithms.KDF_PBKDF2_HMAC_SHA256
        ) {
            throw BackupFormatException.UnsupportedAlgorithm("kdfId", kdfId)
        }

        val fullHeader = ByteArray(headerLength)
        file.seek(0)
        file.readFully(fullHeader)

        val crc = CRC32()
        crc.update(fullHeader, 0, 32)
        crc.update(fullHeader, 36, headerLength - 36)
        if (crc.value.toInt() != headerCrc32) {
            throw BackupFormatException.HeaderCorrupted(path)
        }

        val tailBuf =
            ByteBuffer.wrap(
                fullHeader,
                BackupAlgorithms.HEADER_FIXED_SIZE,
                headerLength - BackupAlgorithms.HEADER_FIXED_SIZE,
            )
        val salt = ByteArray(saltLength).also { tailBuf.get(it) }
        val iv = ByteArray(ivLength).also { tailBuf.get(it) }
        val producerBytes = ByteArray(producerLength).also { tailBuf.get(it) }

        return ContainerHeader(
            formatVersion = formatVersion,
            cipherId = cipherId,
            macId = macId,
            kdfId = kdfId,
            headerLength = headerLength,
            contentType = contentType,
            payloadProfile = payloadProfile,
            kdfIterations = kdfIterations,
            saltLength = saltLength,
            ivLength = ivLength,
            macLength = macLength,
            keyLength = keyLength,
            createdAtWallMillis = createdAtWallMillis,
            plaintextLength = plaintextLength,
            producer = String(producerBytes, Charsets.UTF_8),
            salt = salt,
            iv = iv,
        )
    }
}
