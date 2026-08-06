package io.sanato.logkit

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 导出用 zip,不是拼接——理由见 logkit/README.md:拼接需要再一层长度前缀
 * 容器和第二个恢复扫描器,纯属新增 bug 面;`unzip` 到处都有,支持工程师
 * 能直接读 `manifest.txt` 分诊,不需要这个 SDK 的任何自定义工具。
 *
 * ⚠️ 导出的 zip 不计入 5MB 日志预算——调用方(`LogKitCore.export`)必须已经
 * 先封盘(RotateBarrier + flushBlocking),这里只负责打包已经落盘的文件。
 */
internal object LogExporter {
    fun purgeAll(directory: LogDirectory): Int {
        var count = 0
        directory.list().forEach { file -> if (directory.delete(file)) count++ }
        return count
    }

    fun export(
        directory: LogDirectory,
        destination: File,
        manifestExtra: String = "",
    ): Boolean {
        val files = directory.list().sortedBy { LogFileNaming.parse(it.name)?.fileSeq ?: -1L }
        return try {
            destination.parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { zip ->
                // 密文本身不可压缩,deflate level 0 只是省掉自己手动维护
                // ZipEntry.STORED 要求的 size/crc 字段——见下方注释。
                zip.setLevel(Deflater.NO_COMPRESSION)
                val manifest = StringBuilder()
                manifest.append("logkit export manifest\n")
                manifest.append("fileCount=${files.size}\n")
                files.forEach { file ->
                    manifest.append("file=${file.name} bytes=${file.length()}\n")
                    zip.putNextEntry(ZipEntry(file.name))
                    FileInputStream(file).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
                if (manifestExtra.isNotEmpty()) manifest.append(manifestExtra).append('\n')
                zip.putNextEntry(ZipEntry("manifest.txt"))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            true
        } catch (e: IOException) {
            destination.delete()
            false
        }
    }
}
