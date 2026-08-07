package io.sanato.appkit.backup.archive

import io.sanato.appkit.backup.core.BackupLogger
import io.sanato.appkit.backup.format.BackupFormatException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * 明文归档（zip）的安全解包。
 *
 * normative 安全规则（修复的是历史实现里"先无条件解 media/ 所有 entry、entry 名未净化，
 * 再解析 manifest"这个真实的路径穿越洞——恶意包可以写 `media/../../../shared_prefs/x.xml`）：
 * 1. `manifest.json` 必须是 zip 的第一个 entry（[ZipInputStream] 按物理写入顺序读取，
 *    这个检查挡的是"entry 顺序被构造过的恶意包"，不是 central directory 里的名义顺序）。
 * 2. 媒体 entry 名不得含 `..`、不得是绝对路径；解析后的 canonical path 必须落在
 *    媒体目录内。
 * 3. 若 manifest 声明了非空的 `mediaNames` 白名单（新格式），媒体 entry 名必须精确命中；
 *    legacy manifest 没有这个字段（列表为空），跳过这项交叉校验，安全性仍由第 2 条的
 *    路径净化保证。
 * 4. entry 数与解压总字节有上限，防 zip 炸弹。
 */
internal object ArchiveReader {
    private const val MAX_ENTRIES = 100_000
    private const val MAX_INFLATED_BYTES = 8L * 1024 * 1024 * 1024 // 8 GiB
    private const val BUFFER_SIZE = 64 * 1024

    class ReadResult(
        val manifest: ParsedManifest,
        /** name → 落盘后的文件，仅本次实际解出的媒体。 */
        val extractedMedia: Map<String, File>,
    )

    /** 只读 manifest，不解媒体——用于恢复前的确定性进度预扫（"需要下载多少媒体"）。 */
    fun peekManifest(
        zipFile: File,
        expectedSchema: String,
    ): ParsedManifest {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            val entry = zis.nextEntry
            if (entry == null || entry.name != ArchiveBuilder.MANIFEST_ENTRY) {
                throw BackupFormatException.NotABackupFile(zipFile.path)
            }
            val text = zis.bufferedReader(Charsets.UTF_8).readText()
            return ManifestCodec.decode(text, expectedSchema, zipFile.path)
        }
    }

    fun extract(
        zipFile: File,
        mediaDir: File,
        expectedSchema: String,
        logger: BackupLogger = BackupLogger.None,
    ): ReadResult {
        mediaDir.mkdirs()
        val mediaDirCanonicalPrefix = mediaDir.canonicalFile.path + File.separator

        var manifest: ParsedManifest? = null
        val extracted = mutableMapOf<String, File>()
        var entryCount = 0
        var inflatedBytes = 0L

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            var isFirstEntry = true
            while (entry != null) {
                entryCount++
                check(entryCount <= MAX_ENTRIES) { "zip entry count exceeds $MAX_ENTRIES: ${zipFile.path}" }

                if (isFirstEntry && entry.name != ArchiveBuilder.MANIFEST_ENTRY) {
                    throw BackupFormatException.NotABackupFile(zipFile.path)
                }
                isFirstEntry = false

                when {
                    entry.name == ArchiveBuilder.MANIFEST_ENTRY -> {
                        val text = zis.bufferedReader(Charsets.UTF_8).readText()
                        manifest = ManifestCodec.decode(text, expectedSchema, zipFile.path)
                    }

                    entry.name.startsWith(ArchiveBuilder.MEDIA_DIR) && !entry.isDirectory -> {
                        val currentManifest = manifest
                        val name = entry.name.removePrefix(ArchiveBuilder.MEDIA_DIR)
                        if (currentManifest == null ||
                            !isAcceptableMediaEntry(name, currentManifest, mediaDir, mediaDirCanonicalPrefix)
                        ) {
                            logger.warn("skip unsafe or undeclared media entry: ${entry.name}")
                        } else if (!extracted.containsKey(name)) {
                            // 同名 entry 首个生效，后续跳过（与构建侧的去重行为对称）。
                            val target = File(mediaDir, name)
                            FileOutputStream(target).use { out ->
                                val buf = ByteArray(BUFFER_SIZE)
                                while (true) {
                                    val n = zis.read(buf)
                                    if (n <= 0) break
                                    inflatedBytes += n
                                    check(inflatedBytes <= MAX_INFLATED_BYTES) {
                                        "inflated size exceeds cap: ${zipFile.path}"
                                    }
                                    out.write(buf, 0, n)
                                }
                            }
                            extracted[name] = target
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        val finalManifest = manifest ?: throw BackupFormatException.NotABackupFile(zipFile.path)
        return ReadResult(finalManifest, extracted)
    }

    private fun isAcceptableMediaEntry(
        name: String,
        manifest: ParsedManifest,
        mediaDir: File,
        mediaDirCanonicalPrefix: String,
    ): Boolean {
        if (!isSafeEntryName(name)) return false
        if (manifest.mediaNames.isNotEmpty() && name !in manifest.mediaNames) return false
        val resolved = File(mediaDir, name).canonicalFile
        return resolved.path.startsWith(mediaDirCanonicalPrefix)
    }

    private fun isSafeEntryName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.contains("..")) return false
        if (name.startsWith("/") || name.startsWith("\\")) return false
        if (name.length >= 2 && name[1] == ':') return false // Windows 盘符绝对路径
        return true
    }
}
