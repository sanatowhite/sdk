package io.sanato.appkit.download.store

import io.sanato.appkit.core.net.HttpClientFactory
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * Owns the `<id>.part` / `<id>.meta` pair for every task under one
 * [downloadDir]. Deliberately holds no coroutine/dispatcher state — callers
 * (`queue/DownloadQueue.kt`) decide which thread this runs on. All methods do
 * blocking file I/O.
 */
internal class TaskStore(
    private val downloadDir: File,
) {
    // Reuses :core-net's Json config (ignoreUnknownKeys + explicitNulls=false)
    // rather than standing up a second, presumably-identical instance.
    private val json = HttpClientFactory.defaultJson

    init {
        downloadDir.mkdirs()
    }

    fun partFile(id: String): File = File(downloadDir, "$id.part")

    fun metaFile(id: String): File = File(downloadDir, "$id.meta")

    /** Where the finished file lands once a transfer completes — not necessarily inside [downloadDir]; see [io.sanato.appkit.download.DownloadRequest.destDir]. */
    fun destinationFile(
        destDir: File?,
        fileName: String,
    ): File = File(destDir ?: downloadDir, fileName)

    /**
     * Write-then-rename: `File.renameTo` is atomic on the same filesystem
     * (POSIX `rename(2)`), so a reader never observes a half-written `.meta`
     * file even if the process dies mid-write. Falls back to copy+delete for
     * the rare case `renameTo` fails (e.g. destination on a different
     * filesystem) rather than silently losing the checkpoint.
     */
    fun save(meta: TaskMetadata) {
        val tmp = File(downloadDir, "${meta.id}.meta.tmp")
        tmp.writeText(json.encodeToString(meta))
        val target = metaFile(meta.id)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    fun load(id: String): TaskMetadata? =
        runCatching {
            json.decodeFromString<TaskMetadata>(metaFile(id).readText())
        }.getOrNull()

    /** Every resumable/queued task found on disk, in no particular order — the caller sorts by [TaskMetadata.priority]. */
    fun loadAll(): List<TaskMetadata> =
        downloadDir
            .listFiles { file -> file.name.endsWith(".meta") }
            ?.mapNotNull { file -> runCatching { json.decodeFromString<TaskMetadata>(file.readText()) }.getOrNull() }
            .orEmpty()

    /** Deletes every file belonging to [id] — both the finished-download path (`Completed`) and the give-up path (`Canceled`) call this. */
    fun delete(id: String) {
        metaFile(id).delete()
        partFile(id).delete()
        File(downloadDir, "$id.meta.tmp").delete()
    }
}
