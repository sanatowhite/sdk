package io.sanato.logkit.tools

import io.sanato.logkit.format.LogRecordData
import java.io.File
import java.io.PrintStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

/**
 * 离线解密 `.logkit` 文件的命令行工具。见 logkit/README.md 的"归档格式"一节
 * 与本文件顶部的退出码表——这两处必须保持一致,任何一边改了另一边要跟着改。
 *
 * 退出码:0 正常 / 1 用法错误 / 2 密钥不匹配(keyId 与文件不符)/
 *        3 文件头格式版本不支持 / 4 `--verify-seq` 断言失败。
 */
fun main(args: Array<String>) {
    val parsed =
        try {
            CliArgs.parse(args)
        } catch (e: IllegalArgumentException) {
            System.err.println(e.message)
            System.err.println(USAGE)
            exitProcess(1)
        }

    val privateKeyFile =
        parsed.privateKeyPath?.let(::File)
            ?: System.getenv("LOGKIT_PRIVATE_KEY_FILE")?.let(::File)
    if (privateKeyFile == null || !privateKeyFile.isFile) {
        System.err.println("missing --private-key (or LOGKIT_PRIVATE_KEY_FILE)")
        System.err.println(USAGE)
        exitProcess(1)
    }

    val inputFiles =
        when {
            parsed.input.isDirectory -> {
                parsed.input
                    .listFiles { f -> f.extension == "logkit" }
                    ?.toList()
                    .orEmpty()
            }

            parsed.input.isFile -> {
                listOf(parsed.input)
            }

            else -> {
                emptyList()
            }
        }
    if (inputFiles.isEmpty()) {
        System.err.println("no .logkit files found at ${parsed.input}")
        exitProcess(1)
    }

    val privateKey = PemKeyLoader.loadPrivateKey(privateKeyFile)
    val decodedFiles =
        inputFiles.sortedBy { it.name }.map {
            LogFileDecoder.decode(
                it.readBytes(),
                it.name,
                privateKey,
            )
        }

    val keyMismatches = decodedFiles.filter { it.keyMismatch }
    val formatUnsupported = decodedFiles.filter { it.formatUnsupported }
    val headerUnusable = decodedFiles.filter { it.headerUnusable }

    val out: PrintStream =
        if (parsed.outPath == null ||
            parsed.outPath == "-"
        ) {
            System.out
        } else {
            PrintStream(File(parsed.outPath))
        }

    val allRecords =
        decodedFiles
            .flatMap { file -> file.records.map { RecordWithSource(it, file.sourceFileName) } }
            .sortedBy { it.record.seq } // seq 是唯一全序,从不按时间戳排——见格式文档的"必须写下来的不变量"。

    if (parsed.json) {
        allRecords.forEach { out.println(toJson(it.record, it.sourceFile)) }
    } else {
        allRecords.forEach { out.println(toHumanReadable(it.record)) }
    }

    decodedFiles.forEach { file ->
        val diag = StringBuilder()
        if (file.resyncs > 0) diag.append("resyncs=${file.resyncs} ")
        if (file.authFailures > 0) diag.append("authFailed=${file.authFailures} ")
        if (file.truncatedTailBytes > 0) diag.append("truncatedTailBytes=${file.truncatedTailBytes} ")
        if (diag.isNotEmpty()) System.err.println("${file.sourceFileName}: $diag")
    }

    if (keyMismatches.isNotEmpty()) {
        System.err.println(
            "key mismatch (wrong --private-key) for: ${keyMismatches.joinToString { it.sourceFileName }}",
        )
        exitProcess(2)
    }
    if (formatUnsupported.isNotEmpty()) {
        System.err.println("unsupported formatVersion for: ${formatUnsupported.joinToString { it.sourceFileName }}")
        exitProcess(3)
    }
    if (headerUnusable.isNotEmpty() && parsed.strict) {
        System.err.println("unusable header (strict mode) for: ${headerUnusable.joinToString { it.sourceFileName }}")
        exitProcess(3)
    }

    if (parsed.verifySeq) {
        val seqs = allRecords.map { it.record.seq }
        val hasDuplicate = seqs.size != seqs.toSet().size
        val hasGap = seqs.zipWithNext().any { (a, b) -> b - a > 1 }
        val orderedBySeq = seqs == seqs.sorted()
        if (hasDuplicate || !orderedBySeq) {
            System.err.println("--verify-seq FAILED: duplicate=$hasDuplicate orderedBySeq=$orderedBySeq")
            exitProcess(4)
        }
        if (hasGap) {
            // 空洞不一定是 bug——可能是队列溢出丢弃的记录,合成的 WARN 记录里会
            // 说明丢了多少条。这里只报 WARN,不算失败。
            System.err.println("--verify-seq WARN: gaps found in seq sequence (check for overflow-drop records)")
        }
        System.err.println("--verify-seq OK: ${seqs.size} records, seq strictly ordered, no duplicates")
    }

    exitProcess(0)
}

private data class RecordWithSource(
    val record: LogRecordData,
    val sourceFile: String,
)

private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
private val LEVEL_CHARS = charArrayOf('V', 'D', 'I', 'W', 'E')

private fun toHumanReadable(record: LogRecordData): String {
    val time = ISO_FORMATTER.format(Instant.ofEpochMilli(record.wallMillis))
    val levelChar = LEVEL_CHARS.getOrElse(record.level) { '?' }
    return "$time  $levelChar  seq=${record.seq} ${record.tag}/${record.threadName}: ${record.message}"
}

private fun toJson(
    record: LogRecordData,
    sourceFile: String,
): String {
    fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    return "{\"seq\":${record.seq},\"wallMillis\":${record.wallMillis},\"elapsedNanos\":${record.elapsedNanos}," +
        "\"threadId\":${record.threadId},\"level\":${record.level},\"tag\":\"${esc(record.tag)}\"," +
        "\"threadName\":\"${esc(
            record.threadName,
        )}\",\"message\":\"${esc(record.message)}\",\"sourceFile\":\"${esc(sourceFile)}\"}"
}

private const val USAGE =
    """logkit-decrypt --private-key <pkcs8.pem> --in <file|dir> [--out <file>|-] [--json] [--verify-seq] [--strict]

  --private-key <path>   PKCS#8 PEM 私钥文件。也可用 LOGKIT_PRIVATE_KEY_FILE 环境变量。
                          刻意不提供 --private-key-inline —— 私钥不进 shell history。
  --in <path>             单个 .logkit 文件,或一个目录(读取其中全部 .logkit 文件)。
  --out <path>|-          默认标准输出。
  --json                  每条记录一行 JSON;默认是人类可读格式。
  --verify-seq            断言 seq 全局严格递增且无重复;发现空洞报 WARN 不算失败
                          (可能是队列溢出丢弃,不是 bug)。
  --strict                文件头本身损坏也视为失败(默认只跳过该文件,继续处理其余文件)。

退出码:0 正常 / 1 用法错误 / 2 密钥不匹配 / 3 格式版本不支持 / 4 --verify-seq 失败"""

private class CliArgs(
    val privateKeyPath: String?,
    val input: File,
    val outPath: String?,
    val json: Boolean,
    val verifySeq: Boolean,
    val strict: Boolean,
) {
    companion object {
        fun parse(args: Array<String>): CliArgs {
            var privateKeyPath: String? = null
            var input: String? = null
            var outPath: String? = null
            var json = false
            var verifySeq = false
            var strict = false
            var i = 0
            while (i < args.size) {
                when (args[i]) {
                    "--private-key" -> {
                        privateKeyPath =
                            args.getOrNull(++i) ?: throw IllegalArgumentException("--private-key needs a value")
                    }

                    "--in" -> {
                        input = args.getOrNull(++i) ?: throw IllegalArgumentException("--in needs a value")
                    }

                    "--out" -> {
                        outPath = args.getOrNull(++i) ?: throw IllegalArgumentException("--out needs a value")
                    }

                    "--json" -> {
                        json = true
                    }

                    "--verify-seq" -> {
                        verifySeq = true
                    }

                    "--strict" -> {
                        strict = true
                    }

                    else -> {
                        throw IllegalArgumentException("unknown argument: ${args[i]}")
                    }
                }
                i++
            }
            val inputFile = input?.let(::File) ?: throw IllegalArgumentException("--in is required")
            return CliArgs(privateKeyPath, inputFile, outPath, json, verifySeq, strict)
        }
    }
}
