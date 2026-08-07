package io.sanato.appkit.backup.core

/** 操作阶段，决定进度文案是"上传"/"下载"/"压缩"。 */
public enum class TransferPhase { UPLOADING, DOWNLOADING, COMPRESSING }

/** 备份/恢复的媒体逐文件进度。时间几乎全花在媒体传输上，故以媒体文件数为单位。 */
public class BackupProgress(
    public val phase: TransferPhase,
    public val completed: Int,
    public val total: Int,
)

/** 用 fun interface 而不是 typealias：typealias 在 ABI 快照里只是裸的 Function1，不可读也不可演进。 */
public fun interface ProgressListener {
    public fun onProgress(progress: BackupProgress)
}
