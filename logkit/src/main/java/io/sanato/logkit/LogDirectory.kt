package io.sanato.logkit

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** 确定性测试接缝——测试用 `TemporaryFolder` 支撑的实现或抛 IO 异常的 fake。 */
internal interface LogDirectory {
    fun root(): File

    fun ensureExists()

    fun list(): List<File>

    /** `File.createNewFile()`(`O_CREAT|O_EXCL`)语义:已存在则返回 null,不覆盖。 */
    fun create(name: String): FileSink?

    fun delete(file: File): Boolean
}

/** 单个日志文件的写句柄。[sync] 是 `flushBlocking`/`fatal` 持久性承诺的落点。 */
internal interface FileSink {
    val file: File

    fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    )

    fun sync(): Boolean

    fun length(): Long

    fun close()
}

internal class FileLogDirectory(
    private val root: File,
) : LogDirectory {
    override fun root(): File = root

    override fun ensureExists() {
        if (!root.exists()) root.mkdirs()
    }

    override fun list(): List<File> = root.listFiles()?.toList().orEmpty()

    override fun create(name: String): FileSink? {
        val file = File(root, name)
        return try {
            if (!file.createNewFile()) return null
            FileOutputStreamSink(file)
        } catch (e: IOException) {
            null
        }
    }

    override fun delete(file: File): Boolean = file.delete()
}

internal class FileOutputStreamSink(
    override val file: File,
) : FileSink {
    // append=false——每次都是全新文件,见 LogWriter 的"永不追加已有文件"规则。
    private val stream = FileOutputStream(file, false)
    private var bytesWritten = 0L

    override fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        stream.write(bytes, offset, length)
        bytesWritten += length
    }

    override fun sync(): Boolean =
        try {
            stream.fd.sync()
            true
        } catch (e: IOException) {
            false
        }

    override fun length(): Long = bytesWritten

    override fun close() {
        try {
            stream.close()
        } catch (e: IOException) {
            // 关闭失败无法补救,吞掉——不能让它冒泡到写线程循环外面。
        }
    }
}
