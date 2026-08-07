package io.sanato.appkit.backup.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import io.sanato.appkit.backup.remote.RemoteBackupStore
import io.sanato.appkit.backup.remote.RemoteFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * [RemoteBackupStore] 的 SAF 实现：把"文件夹"映射成用户通过 `OpenDocumentTree` 选定的
 * 目录树下的一级子目录（`media/`、`snapshots/`、`entries/`、`bundles/`），零 GMS、零三方
 * 依赖。用 `DocumentsContract` 直接操作而不是 `DocumentFile`——后者每次调用都会重新发起
 * 一次 `ContentResolver.query`，在文件数多时明显更慢。
 *
 * SDK 不处理 `OpenDocumentTree` 的权限申请与 `takePersistableUriPermission`——那需要
 * Activity 发起，是宿主的事；这个类只消费一个已经拿到读写权限的 [treeUri]。
 *
 * [RemoteFile.id] 在这个实现里就是文档的 content:// URI 字符串，[download]/[delete] 直接
 * `Uri.parse` 回来用。
 */
public class SafBackupStore(
    private val contentResolver: ContentResolver,
    private val treeUri: Uri,
) : RemoteBackupStore {
    private val folderUriCache = mutableMapOf<String, Uri>()

    override suspend fun list(folder: String): List<RemoteFile> {
        val folderUri = ensureFolder(folder) ?: return emptyList()
        val results = mutableListOf<RemoteFile>()
        queryChildren(folderUri) { docId, name, size ->
            results += RemoteFile(id = documentUri(docId).toString(), name = name, size = size)
        }
        return results
    }

    override suspend fun uploadIfAbsent(
        folder: String,
        name: String,
        file: File,
    ): RemoteFile {
        val existing = list(folder).firstOrNull { it.name == name && it.size > 0 }
        if (existing != null) return existing
        return upload(folder, name, file)
    }

    override suspend fun upload(
        folder: String,
        name: String,
        file: File,
    ): RemoteFile {
        val folderUri = ensureFolder(folder) ?: error("SafBackupStore: cannot create folder '$folder'")
        val targetUri =
            findChild(folderUri, name)
                ?: DocumentsContract.createDocument(contentResolver, folderUri, MIME_OCTET_STREAM, name)
                ?: error("SafBackupStore: createDocument failed for '$name' in '$folder'")
        contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
            FileInputStream(file).use { it.copyTo(out) }
        } ?: error("SafBackupStore: openOutputStream failed for $targetUri")
        return RemoteFile(id = targetUri.toString(), name = name, size = file.length())
    }

    override suspend fun download(
        fileId: String,
        dest: File,
    ) {
        val uri = Uri.parse(fileId)
        val input = contentResolver.openInputStream(uri) ?: error("SafBackupStore: openInputStream failed for $uri")
        input.use { ins -> FileOutputStream(dest).use { out -> ins.copyTo(out) } }
    }

    override suspend fun delete(fileId: String) {
        DocumentsContract.deleteDocument(contentResolver, Uri.parse(fileId))
    }

    private fun ensureFolder(name: String): Uri? {
        folderUriCache[name]?.let { return it }
        val rootUri = documentUri(DocumentsContract.getTreeDocumentId(treeUri))
        val folderUri =
            findChild(rootUri, name)
                ?: DocumentsContract.createDocument(
                    contentResolver,
                    rootUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    name,
                )
        if (folderUri != null) folderUriCache[name] = folderUri
        return folderUri
    }

    private fun findChild(
        parentUri: Uri,
        name: String,
    ): Uri? {
        var found: Uri? = null
        queryChildren(parentUri) { docId, childName, _ ->
            if (found == null && childName == name) found = documentUri(docId)
        }
        return found
    }

    private inline fun queryChildren(
        parentUri: Uri,
        onChild: (docId: String, name: String, size: Long) -> Unit,
    ) {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getDocumentId(parentUri),
            )
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
            )
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                onChild(cursor.getString(0), cursor.getString(1), cursor.getLong(2))
            }
        }
    }

    private fun documentUri(docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    private companion object {
        const val MIME_OCTET_STREAM = "application/octet-stream"
    }
}
