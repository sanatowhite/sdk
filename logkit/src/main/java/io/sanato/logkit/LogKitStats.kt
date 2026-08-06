package io.sanato.logkit

import java.io.File

/** 一个日志文件的展示信息,给 debug 面板用。非 `data class`——理由见 [LogKitConfig]。 */
public class LogFileInfo internal constructor(
    public val name: String,
    public val sizeBytes: Long,
    public val fileSeq: Long,
) {
    override fun toString(): String = "LogFileInfo(name=$name, sizeBytes=$sizeBytes, fileSeq=$fileSeq)"
}

/** [LogKit.stats] 的返回类型——一次快照,不是实时 Flow。见 `DebugDrawerContent` 里手动 refresh 的先例。 */
public class LogKitStats internal constructor(
    public val files: List<LogFileInfo>,
    public val totalBytes: Long,
    public val budgetBytes: Long,
    public val queuedRecords: Int,
    public val nextSequence: Long,
    public val droppedRecords: Long,
    public val evictedFiles: Long,
    public val persistenceHealthy: Boolean,
    public val keyId: Int,
    public val formatVersion: Int,
)

internal fun buildStats(
    files: List<File>,
    lengthOf: (File) -> Long,
    totalBytes: Long,
    budgetBytes: Long,
    queuedRecords: Int,
    nextSequence: Long,
    droppedRecords: Long,
    evictedFiles: Long,
    persistenceHealthy: Boolean,
    keyId: Int,
): LogKitStats {
    val infos =
        files.map { file ->
            val parsed = LogFileNaming.parse(file.name)
            LogFileInfo(file.name, lengthOf(file), parsed?.fileSeq ?: -1L)
        }
    return LogKitStats(
        infos,
        totalBytes,
        budgetBytes,
        queuedRecords,
        nextSequence,
        droppedRecords,
        evictedFiles,
        persistenceHealthy,
        keyId,
        io.sanato.logkit.format.FileHeaderCodec.FORMAT_VERSION,
    )
}
