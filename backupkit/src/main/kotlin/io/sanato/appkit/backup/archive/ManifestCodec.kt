package io.sanato.appkit.backup.archive

import io.sanato.appkit.backup.core.BackupRecord
import io.sanato.appkit.backup.core.ManifestHeader
import io.sanato.appkit.backup.format.BackupFormatException
import org.json.JSONArray
import org.json.JSONObject

/**
 * manifest.json 的编解码。新格式的 schema 契约：
 *
 * ```json
 * {
 *   "manifestVersion": 1,
 *   "producer": "...",
 *   "payloadSchema": "io.sanato.diary.journal",
 *   "payloadSchemaVersion": 4,
 *   "createdAt": 1730000000000,
 *   "recordCount": 128,
 *   "mediaNames": ["IMG_1730.jpg", "VOICE_9.m4a"],
 *   "records": [{"id":"1024","createdAt":...,"modifiedAt":...,"media":["IMG_1730.jpg"],"body":"..."}]
 * }
 * ```
 *
 * `manifestVersion` 归 backupkit 管，未知即报错——**必须真的读、真的校验**，这是本次
 * 设计要补的核心防线（legacy 格式的教训：manifest 写了 `version` 字段却从来没被恢复
 * 逻辑读过）。`payloadSchema`/`payloadSchemaVersion`/`records[].body` 对 backupkit 是
 * 不透明内容，归宿主解释。
 *
 * legacy 识别：`{"version":3,"notes":[...]}`（有 `version` 无 `manifestVersion`）这个
 * 形状被适配成 `manifestVersion=0`（哨兵值，表示 legacy）的信封——`payloadSchemaVersion`
 * 取旧字段 `version`，跳过 schema 名比对（legacy 包没有这个概念），每条 note 的整个
 * JSON 对象原样序列化塞进 `body`，`media` 恒为空列表（legacy 正文里的媒体引用要靠
 * 宿主自己的正则扫，不是 backupkit 能通用化的东西）。
 */
internal object ManifestCodec {
    private const val MANIFEST_VERSION = 1
    private const val LEGACY_MANIFEST_VERSION_SENTINEL = 0

    fun encode(
        payloadSchema: String,
        payloadSchemaVersion: Int,
        producer: String,
        createdAtMillis: Long,
        mediaNames: List<String>,
        records: List<BackupRecord>,
    ): String {
        val recordsArray = JSONArray()
        for (record in records) {
            recordsArray.put(
                JSONObject().apply {
                    put("id", record.id)
                    put("createdAt", record.createdAtMillis)
                    put("modifiedAt", record.modifiedAtMillis)
                    put("media", JSONArray(record.mediaNames))
                    put("body", record.body)
                },
            )
        }
        val root =
            JSONObject().apply {
                put("manifestVersion", MANIFEST_VERSION)
                put("producer", producer)
                put("payloadSchema", payloadSchema)
                put("payloadSchemaVersion", payloadSchemaVersion)
                put("createdAt", createdAtMillis)
                put("recordCount", records.size)
                put("mediaNames", JSONArray(mediaNames))
                put("records", recordsArray)
            }
        return root.toString()
    }

    /**
     * @param expectedSchema 调用方声明的 payloadSchema；new-format manifest 必须与之一致
     * （防止把别的 app 的备份包灌进来），legacy manifest 跳过这项校验。
     */
    fun decode(
        text: String,
        expectedSchema: String,
        path: String,
    ): ParsedManifest {
        val root =
            runCatching { JSONObject(text) }.getOrElse {
                throw BackupFormatException.NotABackupFile(path)
            }

        return if (root.has("manifestVersion")) {
            decodeCurrent(root, expectedSchema)
        } else if (root.has("notes")) {
            decodeLegacy(root)
        } else {
            throw BackupFormatException.NotABackupFile(path)
        }
    }

    private fun decodeCurrent(
        root: JSONObject,
        expectedSchema: String,
    ): ParsedManifest {
        val manifestVersion = root.optInt("manifestVersion", -1)
        if (manifestVersion != MANIFEST_VERSION) {
            throw BackupFormatException.UnsupportedManifestVersion(manifestVersion, MANIFEST_VERSION..MANIFEST_VERSION)
        }
        val payloadSchema = root.optString("payloadSchema", "")
        if (payloadSchema != expectedSchema) {
            throw BackupFormatException.SchemaMismatch(expectedSchema, payloadSchema)
        }
        val payloadSchemaVersion = root.optInt("payloadSchemaVersion", 0)
        val createdAtMillis = root.optLong("createdAt", -1L)
        val mediaNames = root.optJSONArray("mediaNames")?.toStringList() ?: emptyList()
        val recordsArray = root.optJSONArray("records") ?: JSONArray()

        val records = mutableListOf<BackupRecord>()
        for (i in 0 until recordsArray.length()) {
            val obj = recordsArray.optJSONObject(i) ?: continue
            records +=
                BackupRecord(
                    id = obj.optString("id", ""),
                    createdAtMillis = obj.optLong("createdAt", 0L),
                    modifiedAtMillis = obj.optLong("modifiedAt", 0L),
                    body = obj.optString("body", ""),
                    mediaNames = obj.optJSONArray("media")?.toStringList() ?: emptyList(),
                )
        }

        return ParsedManifest(
            header =
                ManifestHeader(
                    manifestVersion = manifestVersion,
                    payloadSchema = payloadSchema,
                    payloadSchemaVersion = payloadSchemaVersion,
                    recordCount = records.size,
                    createdAtMillis = createdAtMillis,
                ),
            mediaNames = mediaNames,
            records = records,
        )
    }

    private fun decodeLegacy(root: JSONObject): ParsedManifest {
        val legacyVersion = root.optInt("version", 0)
        val notes = root.optJSONArray("notes") ?: JSONArray()
        val records = mutableListOf<BackupRecord>()
        for (i in 0 until notes.length()) {
            val obj = notes.optJSONObject(i) ?: continue
            records +=
                BackupRecord(
                    id = obj.optString("id", obj.optLong("id", i.toLong()).toString()),
                    createdAtMillis = obj.optLong("createdAt", 0L),
                    modifiedAtMillis = obj.optLong("modifiedAt", 0L),
                    body = obj.toString(),
                    mediaNames = emptyList(),
                )
        }
        return ParsedManifest(
            header =
                ManifestHeader(
                    manifestVersion = LEGACY_MANIFEST_VERSION_SENTINEL,
                    payloadSchema = "",
                    payloadSchemaVersion = legacyVersion,
                    recordCount = records.size,
                    createdAtMillis = root.optLong("exportTime", -1L),
                ),
            mediaNames = emptyList(),
            records = records,
        )
    }

    private fun JSONArray.toStringList(): List<String> = (0 until length()).mapNotNull { optString(it, null) }
}
