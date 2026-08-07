package io.sanato.appkit.backup.testing

import io.sanato.appkit.backup.core.BackupDataSource
import io.sanato.appkit.backup.core.BackupRecord
import io.sanato.appkit.backup.core.RecordSummary
import java.io.File

/** 纯内存 [BackupDataSource] 假实现，供编排层/归档层单测使用。 */
class FakeBackupDataSource(
    override val payloadSchema: String = "test.schema",
    override val payloadSchemaVersion: Int = 1,
) : BackupDataSource {
    private val records = linkedMapOf<String, BackupRecord>()
    private val mediaFiles = mutableMapOf<String, File>()

    fun seed(record: BackupRecord) {
        records[record.id] = record
    }

    fun seedMedia(
        name: String,
        file: File,
    ) {
        mediaFiles[name] = file
    }

    fun remove(id: String) {
        records.remove(id)
    }

    override suspend fun listRecordSummaries(): List<RecordSummary> =
        records.values.map { RecordSummary(it.id, it.createdAtMillis, it.modifiedAtMillis) }

    override suspend fun loadRecord(id: String): BackupRecord? = records[id]

    override suspend fun resolveLocalMedia(name: String): File? = mediaFiles[name]
}
