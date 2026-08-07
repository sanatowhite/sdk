package io.sanato.appkit.backup.remote

import java.io.File

/** 远端一个已存在文件的最小描述。 */
public class RemoteFile(
    public val id: String,
    public val name: String,
    public val size: Long,
)

/** 远端备份存储的最小抽象：按"文件夹名"分区操作。SAF 实现见 `saf.SafBackupStore`，Google Drive 实现见 `:backupkit-drive`。 */
public interface RemoteBackupStore {
    /** 列出某文件夹下的文件（已过滤已删除/回收站里的）。 */
    public suspend fun list(folder: String): List<RemoteFile>

    /** 不存在（或远端 size<=0）才上传；已存在则原样返回，不重传——增量备份的断点续传靠这个语义。 */
    public suspend fun uploadIfAbsent(
        folder: String,
        name: String,
        file: File,
    ): RemoteFile

    /** 创建或覆盖上传。 */
    public suspend fun upload(
        folder: String,
        name: String,
        file: File,
    ): RemoteFile

    public suspend fun download(
        fileId: String,
        dest: File,
    )

    public suspend fun delete(fileId: String)

    public companion object {
        public const val FOLDER_MEDIA: String = "media"
        public const val FOLDER_SNAPSHOTS: String = "snapshots"
        public const val FOLDER_ENTRIES: String = "entries"
        public const val FOLDER_BUNDLES: String = "bundles"
    }
}
