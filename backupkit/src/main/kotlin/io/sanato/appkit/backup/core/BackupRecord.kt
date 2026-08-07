package io.sanato.appkit.backup.core

import java.io.File

/**
 * 宿主自有的一条备份记录。[body] 对 backupkit 完全不透明——日记的标题/正文/心情/标签
 * 等字段全部由宿主自行编码进这个字符串，backupkit 只负责把它原样写进容器并原样吐回来。
 *
 * 用普通 class 而不是 data class：data class 的 `copy()`/`componentN()` 一旦发布就冻进
 * ABI，将来加一个字段就会破坏二进制兼容。
 */
public class BackupRecord(
    public val id: String,
    public val createdAtMillis: Long,
    public val modifiedAtMillis: Long,
    public val body: String,
    public val mediaNames: List<String>,
)

/** 编排层需要的最小记录元信息（不含正文），列全量摘要时不必把每条记录的内容都拉进内存。 */
public class RecordSummary(
    public val id: String,
    public val createdAtMillis: Long,
    public val modifiedAtMillis: Long,
)

/** 导出侧：宿主提供要备份的东西。 */
public interface BackupDataSource {
    /** 宿主自定义的负载 schema 名，写进 manifest，恢复时用于校验"这是不是我认识的备份"。 */
    public val payloadSchema: String

    /** schema 的版本号，SDK 不解释，原样透传给恢复侧的 [BackupRestoreTarget.onRestoreStart]。 */
    public val payloadSchemaVersion: Int

    public suspend fun listRecordSummaries(): List<RecordSummary>

    public suspend fun loadRecord(id: String): BackupRecord?

    /** 媒体名 → 本机文件；找不到返回 null（文本照常备份，绝不因媒体缺失而失败）。 */
    public suspend fun resolveLocalMedia(name: String): File?
}
