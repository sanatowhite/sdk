package io.sanato.appkit.backup.testing

import io.sanato.appkit.backup.core.BackupRecord
import io.sanato.appkit.backup.core.BackupRestoreTarget
import io.sanato.appkit.backup.core.ManifestHeader
import java.io.File

/** 纯内存 [BackupRestoreTarget] 假实现——不做去重，逐条接受，供编排层/归档层单测使用。 */
class FakeBackupRestoreTarget(
    private val mediaDir: File,
) : BackupRestoreTarget {
    val accepted = mutableListOf<BackupRecord>()
    val resolvedMediaByRecordId = mutableMapOf<String, Map<String, File>>()
    var lastHeader: ManifestHeader? = null
    var finishedCount: Int? = null

    override fun mediaDirectory(): File = mediaDir

    override suspend fun onRestoreStart(header: ManifestHeader) {
        lastHeader = header
    }

    override suspend fun accept(
        record: BackupRecord,
        resolvedMedia: Map<String, File>,
    ): Boolean {
        accepted += record
        resolvedMediaByRecordId[record.id] = resolvedMedia
        return true
    }

    override suspend fun onRestoreFinish(restoredCount: Int) {
        finishedCount = restoredCount
    }
}
