package io.sanato.appkit.backup.core

import java.io.File

/** 恢复的 manifest 头信息，交付给 [BackupRestoreTarget.onRestoreStart] 供宿主建索引用。 */
public class ManifestHeader(
    public val manifestVersion: Int,
    public val payloadSchema: String,
    public val payloadSchemaVersion: Int,
    public val recordCount: Int,
    public val createdAtMillis: Long,
)

/**
 * 恢复侧：backupkit 只负责解出记录、按名补齐媒体，写库/去重/内容优选/事务全部归宿主。
 *
 * ⚠️ backupkit 不提供事务或回滚保证：[accept] 抛异常时，此前已经 accept 成功的记录不会
 * 被撤销——这与常见备份实现的行为一致（靠幂等收敛而不是原子性），如果宿主需要更强的
 * 保证，需要自己在 [accept] 内部实现。
 */
public interface BackupRestoreTarget {
    /**
     * 媒体落盘目录——backupkit 从包内解出的媒体、或按需从远端补齐的媒体都会落到这里。
     * 宿主对同一个恢复目标应始终返回同一个目录（backupkit 会用它做"本机已有就跳过下载"
     * 的短路判断）。
     */
    public fun mediaDirectory(): File

    /**
     * 恢复开始前的回调——宿主在这里按需建索引（如按 createdAt 分桶）。⚠️ 一次
     * [io.sanato.appkit.backup.remote.BackupOrchestrator.restore] 调用可能依次导入多个
     * 归档（快照 + 若干增量条目，必要时再加兜底整包），本回调会随每个归档各触发一次，
     * 不是整次 restore() 只触发一次——宿主的索引构建逻辑需要能安全地重复调用。
     */
    public suspend fun onRestoreStart(header: ManifestHeader) {}

    /**
     * 宿主可选实现：为一条记录声明"除了 [BackupRecord.mediaNames] 之外还需要哪些媒体名"。
     *
     * 存在原因：legacy manifest（`ManifestCodec.decodeLegacy()` 产出的记录）的
     * [BackupRecord.mediaNames] 恒为空——这是刻意的设计（SDK 不解析宿主自定义的 legacy
     * body 格式）。对本地/SAF 的自包含归档来说这不是问题：包内 media/ 条目已被
     * `ArchiveReader.extract()` 无条件解到 [mediaDirectory]，宿主在 [accept] 内部按正文
     * 扫描 + 本地目录查找也能找到。但对 Google Drive 这类"媒体单独存一个远端库、按需下载"
     * 的场景，[BackupOrchestrator][io.sanato.appkit.backup.remote.BackupOrchestrator] 只会
     * 下载 [BackupRecord.mediaNames] 里列出的名字——legacy 记录该字段为空，导致远端明明
     * 有对应媒体文件，也永远不会触发下载。
     *
     * 只有宿主知道 [BackupRecord.body] 里怎么引用媒体（backupkit 对 body 完全不透明），
     * 所以这个缺口只能由宿主实现补上：从 body 里扫出实际引用到的媒体名返回，
     * backupkit 会把这些名字并入 [accept] 的按需下载范围。默认空集，不影响新格式记录
     * （mediaNames 已经声明齐全）和不需要这个能力的宿主。
     */
    public suspend fun additionalMediaNames(record: BackupRecord): Set<String> = emptySet()

    /**
     * 逐条交付一条记录。[resolvedMedia] 的 key 是 manifest 里的媒体名，value 是已经落到
     * 本机的文件——backupkit 已经从包内解出、或按需从远端存储补齐好（补齐过程全程
     * suspend，不存在任何 runBlocking 桥接）。
     *
     * 返回 true 计入恢复计数；去重、内容优选、乱码惩罚等业务判断全部在这里实现，
     * backupkit 不介入。
     */
    public suspend fun accept(
        record: BackupRecord,
        resolvedMedia: Map<String, File>,
    ): Boolean

    public suspend fun onRestoreFinish(restoredCount: Int) {}
}
