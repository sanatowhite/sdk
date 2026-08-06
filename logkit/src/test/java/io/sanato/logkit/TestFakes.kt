package io.sanato.logkit

import io.sanato.logkit.format.FrameCodec
import java.io.File
import java.io.IOException

/**
 * `Crypto` fake——不碰任何 ECDH/HKDF,`sealFrame` 直接把明文原样(deflate=false,
 * 不加密)拼上 16 个零字节充当"tag",走完全一样的 [FrameCodec] 头部/CRC 编码。
 * 分帧/滚动/淘汰测试因此完全不受密码学实现细节影响,又仍然产出结构合法的帧。
 */
internal class IdentityCrypto : Crypto {
    private var nextFileSeq = 0L

    override fun newFileKeys(fileSeq: Long): FileKeys {
        nextFileSeq = fileSeq
        return FileKeys(
            contentKey = ByteArray(32) { 1 },
            wrappedKey = ByteArray(io.sanato.logkit.format.Envelope.WRAPPED_KEY_LEN),
            nonceSalt = byteArrayOf(0, 0, 0, fileSeq.toByte()),
            keyId = 1,
        )
    }

    override fun sealFrame(
        keys: FileKeys,
        frameIndex: Long,
        firstRecordSeq: Long,
        recordCount: Int,
        plaintext: ByteArray,
        compress: Boolean,
        flushInduced: Boolean,
    ): ByteArray =
        FrameCodec.seal(
            keys.contentKey,
            keys.nonceSalt,
            frameIndex,
            firstRecordSeq,
            recordCount,
            plaintext,
            compress = false,
            flushInduced,
        )
}

/** 建文件永远失败——测试 crypto 不可用时"目录里一个文件都不会出现"。 */
internal class AlwaysFailingCrypto : Crypto {
    override fun newFileKeys(fileSeq: Long): FileKeys? = null

    override fun sealFrame(
        keys: FileKeys,
        frameIndex: Long,
        firstRecordSeq: Long,
        recordCount: Int,
        plaintext: ByteArray,
        compress: Boolean,
        flushInduced: Boolean,
    ): ByteArray = throw IllegalStateException("should never be called")
}

/** 每次 `poll` 立即返回——让测试用手动调用驱动写线程的每一步,不依赖真实计时。 */
internal class ManualPollQueue : RecordQueue {
    private val backing = ArrayDeque<QueueItem>()

    @Synchronized
    override fun offer(item: QueueItem): Boolean {
        backing.addLast(item)
        return true
    }

    @Synchronized
    override fun poll(timeoutMillis: Long): QueueItem? = if (backing.isEmpty()) null else backing.removeFirst()

    @Synchronized
    override fun drainTo(
        out: MutableList<QueueItem>,
        max: Int,
    ): Int {
        var n = 0
        while (backing.isNotEmpty() && n < max) {
            out.add(backing.removeFirst())
            n++
        }
        return n
    }

    @Synchronized
    override fun approxSize(): Int = backing.size
}

/** 第 N 次 `directory.create(...)` 之后的每一次写入都抛 IOException,模拟 ENOSPC。 */
internal class FlakyLogDirectory(
    private val delegate: LogDirectory,
    private val failWritesFromCallIndex: Int,
) : LogDirectory {
    private var writeCallCount = 0

    override fun root(): File = delegate.root()

    override fun ensureExists() = delegate.ensureExists()

    override fun list(): List<File> = delegate.list()

    override fun create(name: String): FileSink? {
        val real = delegate.create(name) ?: return null
        return object : FileSink {
            override val file: File = real.file

            override fun write(
                bytes: ByteArray,
                offset: Int,
                length: Int,
            ) {
                writeCallCount++
                if (writeCallCount >= failWritesFromCallIndex) throw IOException("simulated ENOSPC")
                real.write(bytes, offset, length)
            }

            override fun sync(): Boolean = real.sync()

            override fun length(): Long = real.length()

            override fun close() = real.close()
        }
    }

    override fun delete(file: File): Boolean = delegate.delete(file)
}
