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
