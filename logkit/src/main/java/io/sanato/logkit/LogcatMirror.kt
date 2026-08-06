package io.sanato.logkit

import android.util.Log
import io.sanato.logkit.format.LogRecordData

/**
 * 唯一触碰 `android.util.Log` 的文件——隔离它是让其余全部类都能用纯 JVM
 * JUnit 测试的原因。按 logcat 单条 ~4000 字节的上限分片,超长消息不会被
 * logcat 静默截断在中间。
 */
internal object LogcatMirror {
    private const val CHUNK_SIZE = 4000

    fun mirror(record: LogRecordData) {
        val priority = LogLevel.fromWireValue(record.level).logcatPriority
        val message = record.message
        if (message.length <= CHUNK_SIZE) {
            Log.println(priority, record.tag, message)
            return
        }
        var offset = 0
        while (offset < message.length) {
            val end = minOf(offset + CHUNK_SIZE, message.length)
            Log.println(priority, record.tag, message.substring(offset, end))
            offset = end
        }
    }
}
