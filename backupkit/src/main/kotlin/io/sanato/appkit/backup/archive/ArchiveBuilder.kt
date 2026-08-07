package io.sanato.appkit.backup.archive

import io.sanato.appkit.backup.core.BackupRecord
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 明文归档（zip）的构建：`manifest.json`（必须是第一个 entry） + 可选的 `media/<name>`。 */
internal object ArchiveBuilder {
    const val MANIFEST_ENTRY = "manifest.json"
    const val MEDIA_DIR = "media/"

    class BuildResult(
        /** 本次记录集合引用到的媒体文件，按 name 去重、首次出现顺序。供调用方在
         * PROFILE_MANIFEST_ONLY 模式下把它们单独上传到媒体库。 */
        val mediaFiles: List<File>,
    )

    suspend fun build(
        outputZip: File,
        payloadSchema: String,
        payloadSchemaVersion: Int,
        producer: String,
        createdAtMillis: Long,
        records: List<BackupRecord>,
        includeMediaBytes: Boolean,
        resolveMedia: suspend (String) -> File?,
    ): BuildResult {
        val mediaFiles = LinkedHashMap<String, File>()
        for (record in records) {
            for (name in record.mediaNames) {
                if (mediaFiles.containsKey(name)) continue
                val file = resolveMedia(name) ?: continue
                if (file.exists()) mediaFiles[name] = file
            }
        }

        val manifestJson =
            ManifestCodec.encode(
                payloadSchema = payloadSchema,
                payloadSchemaVersion = payloadSchemaVersion,
                producer = producer,
                createdAtMillis = createdAtMillis,
                mediaNames = mediaFiles.keys.toList(),
                records = records,
            )

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZip))).use { zos ->
            // manifest 必须是第一个 entry —— ArchiveReader 的安全解包依赖这个顺序：
            // 先读到 manifest、校验通过，才知道该信哪些媒体名。
            zos.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            if (includeMediaBytes) {
                for ((name, file) in mediaFiles) {
                    zos.putNextEntry(ZipEntry(MEDIA_DIR + name))
                    FileInputStream(file).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        return BuildResult(mediaFiles.values.toList())
    }
}
