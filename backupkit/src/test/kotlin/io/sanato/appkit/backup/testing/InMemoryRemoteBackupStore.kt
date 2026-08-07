package io.sanato.appkit.backup.testing

import io.sanato.appkit.backup.remote.RemoteBackupStore
import io.sanato.appkit.backup.remote.RemoteFile
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** [RemoteBackupStore] 的内存假实现，供编排层单测用——不依赖真实网络/SAF。 */
class InMemoryRemoteBackupStore : RemoteBackupStore {
    private class Entry(
        val id: String,
        var name: String,
        var bytes: ByteArray,
    )

    private val foldersById = mutableMapOf<String, MutableMap<String, Entry>>() // folder -> id -> entry
    private val nextId = AtomicLong(1)

    var failNextUpload: Boolean = false
    var deletedIds = mutableListOf<String>()

    override suspend fun list(folder: String): List<RemoteFile> =
        foldersById[folder].orEmpty().values.map { RemoteFile(it.id, it.name, it.bytes.size.toLong()) }

    override suspend fun uploadIfAbsent(
        folder: String,
        name: String,
        file: File,
    ): RemoteFile {
        val existing = foldersById[folder]?.values?.firstOrNull { it.name == name && it.bytes.isNotEmpty() }
        if (existing != null) return RemoteFile(existing.id, existing.name, existing.bytes.size.toLong())
        return upload(folder, name, file)
    }

    override suspend fun upload(
        folder: String,
        name: String,
        file: File,
    ): RemoteFile {
        if (failNextUpload) {
            failNextUpload = false
            throw java.io.IOException("simulated upload failure")
        }
        val bucket = foldersById.getOrPut(folder) { mutableMapOf() }
        val existingId = bucket.values.firstOrNull { it.name == name }?.id
        val id = existingId ?: "id-${nextId.getAndIncrement()}"
        bucket[id] = Entry(id, name, file.readBytes())
        return RemoteFile(id, name, file.length())
    }

    override suspend fun download(
        fileId: String,
        dest: File,
    ) {
        val entry =
            foldersById.values.flatMap { it.values }.firstOrNull { it.id == fileId }
                ?: throw java.io.FileNotFoundException(fileId)
        dest.writeBytes(entry.bytes)
    }

    override suspend fun delete(fileId: String) {
        deletedIds += fileId
        for (bucket in foldersById.values) {
            if (bucket.remove(fileId) != null) return
        }
    }
}
