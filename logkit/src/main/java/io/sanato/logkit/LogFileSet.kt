package io.sanato.logkit

import java.io.File

/**
 * 文件名 = `logkit-<fileSeq:%012d>-<createdAtWallMillis:%013d>-<processTag>.logkit`。
 * `fileSeq` 零填充 12 位 ⇒ 字典序 == 创建序,读方无需解析就能排序;`fileSeq`
 * 同时也写进文件头,重命名/复制之后依然可排序。
 */
internal object LogFileNaming {
    private const val PREFIX = "logkit-"
    private const val SUFFIX = ".logkit"
    private val PATTERN = Regex("""^logkit-(\d{12})-(\d{1,13})-([A-Za-z0-9_]{1,23})\.logkit$""")

    data class ParsedName(
        val fileSeq: Long,
        val createdAtWallMillis: Long,
        val processTag: String,
    )

    fun buildName(
        fileSeq: Long,
        createdAtWallMillis: Long,
        processTag: String,
    ): String {
        val seqPart = fileSeq.toString().padStart(12, '0')
        val millisPart = createdAtWallMillis.toString().padStart(13, '0')
        return "$PREFIX$seqPart-$millisPart-$processTag$SUFFIX"
    }

    /** 解析失败(损坏/外来文件)返回 null——调用方把这类文件的 fileSeq 当作 -1,最先淘汰。 */
    fun parse(name: String): ParsedName? {
        val match = PATTERN.matchEntire(name) ?: return null
        val seq = match.groupValues[1].toLongOrNull() ?: return null
        val millis = match.groupValues[2].toLongOrNull() ?: 0L
        return ParsedName(seq, millis, match.groupValues[3])
    }
}

/**
 * 目录里当前文件集合的内存模型 + 滚动/淘汰的纯逻辑。"最旧" = 最小 `fileSeq`,
 * 绝不用 mtime——理由(用户改日期/NTP 校正/1s mtime 粒度/adb pull 重写 mtime)
 * 见 logkit/README.md。淘汰时跳过当前打开的文件,并跳过每个 `processTag`
 * 的最大 `fileSeq`(多进程场景下不能删别的进程正在写的最新文件)。
 */
internal class LogFileSet(
    private val directory: LogDirectory,
    private val maxFileBytes: Long,
    private val totalBudgetBytes: Long,
    private val maxFileCount: Int,
    private val diagnostics: Diagnostics,
) {
    private class TrackedFile(
        val file: File,
        val fileSeq: Long,
        val processTag: String,
        var length: Long,
    )

    private val tracked = mutableListOf<TrackedFile>()
    private var incrementalTotal = 0L
    private var maxKnownFileSeq = -1L

    /** 写线程的第一个工作项——列目录、按名字解析、决定下一个 fileSeq。做 IO,不能在调用方线程跑。 */
    fun initialize() {
        directory.ensureExists()
        rescanFromDisk()
        evict(openFile = null)
    }

    fun nextFileSeq(): Long = ++maxKnownFileSeq

    fun recordCreated(
        file: File,
        fileSeq: Long,
        processTag: String,
    ) {
        tracked.add(TrackedFile(file, fileSeq, processTag, 0L))
        if (fileSeq > maxKnownFileSeq) maxKnownFileSeq = fileSeq
    }

    fun recordWritten(
        file: File,
        deltaBytes: Long,
    ) {
        val entry = tracked.find { it.file == file } ?: return
        entry.length += deltaBytes
        incrementalTotal += deltaBytes
    }

    fun lengthOf(file: File): Long = tracked.find { it.file == file }?.length ?: 0L

    fun shouldRotate(
        currentFileBytes: Long,
        nextFrameBytes: Long,
    ): Boolean = currentFileBytes + nextFrameBytes > maxFileBytes

    /** [openFile] 永不被淘汰。返回被删除的文件数,供 [Diagnostics.evictedFiles] 之外的调用方逐次判断用。 */
    fun evict(openFile: File?): Int {
        if (incrementalTotal <= totalBudgetBytes && tracked.size <= maxFileCount) return 0

        val newestSeqPerTag = tracked.groupBy { it.processTag }.mapValues { (_, files) -> files.maxOf { it.fileSeq } }
        val candidates =
            tracked
                .filter { it.file != openFile && it.fileSeq != newestSeqPerTag[it.processTag] }
                .sortedBy { it.fileSeq } // 最小 fileSeq = 最旧,绝不用 mtime。

        var evicted = 0
        for (candidate in candidates) {
            if (incrementalTotal <= totalBudgetBytes && tracked.size <= maxFileCount) break
            val deleted = directory.delete(candidate.file)
            tracked.remove(candidate)
            if (deleted) {
                incrementalTotal -= candidate.length
                diagnostics.evictedFiles.incrementAndGet()
                evicted++
            }
            // 删除失败(被并发进程先删、或无权限)——记账里已经移除这个条目,继续下一个。
        }
        return evicted
    }

    fun files(): List<File> = tracked.sortedBy { it.fileSeq }.map { it.file }

    fun totalBytes(): Long = incrementalTotal

    fun fileCount(): Int = tracked.size

    private fun rescanFromDisk() {
        tracked.clear()
        incrementalTotal = 0
        var maxSeq = -1L
        for (file in directory.list()) {
            val parsed = LogFileNaming.parse(file.name)
            val seq = parsed?.fileSeq ?: -1L
            val tag = parsed?.processTag ?: ""
            val length = file.length()
            tracked.add(TrackedFile(file, seq, tag, length))
            incrementalTotal += length
            if (seq > maxSeq) maxSeq = seq
        }
        maxKnownFileSeq = maxSeq
    }
}
