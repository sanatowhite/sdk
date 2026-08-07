package io.sanato.appkit.backup.format

/**
 * 容器格式相关的异常层级。核心诉求：`formatVersion`/算法字段不认识时必须是明确的报错，
 * 不能被当成"口令错了"或"不是备份文件"含糊过去——旧格式(SDB1/SDB2)manifest 写了
 * `version` 字段却从不校验正是这个问题的真实案例。
 */
public sealed class BackupFormatException(
    message: String,
) : Exception(message) {
    /** 文件太短，连固定头都装不下，或明显不是本格式（既非 SBK1 也非已知 legacy 格式）。 */
    public class NotABackupFile(
        public val path: String,
    ) : BackupFormatException("Not a recognizable backup file: $path")

    /** `formatVersion` 是未知值。绝不降级成其它错误——升级 SDK 前的旧版本读到更新的包时，用户能看懂"版本太新"而不是"包坏了"。 */
    public class UnsupportedFormatVersion(
        public val found: Int,
        public val supportedRange: IntRange,
    ) : BackupFormatException("Unsupported formatVersion=$found (supported: $supportedRange)")

    /** `cipherId`/`macId`/`kdfId` 之一是未知值，即使 formatVersion 已知——新增算法不需要 bump formatVersion，但旧 SDK 必须能明确认出"这个算法我不认识"。 */
    public class UnsupportedAlgorithm(
        public val field: String,
        public val value: Int,
    ) : BackupFormatException("Unsupported $field=$value")

    /** 头部 CRC32 校验失败——文件在传输/存储中已损坏。这是未认证的完整性检查，只挡意外损坏。 */
    public class HeaderCorrupted(
        public val path: String,
    ) : BackupFormatException("Header CRC32 mismatch, file corrupted: $path")

    /** MAC 校验失败：口令错误，或文件被篡改。fail-closed——在这个异常之前不会向调用方交付任何一个明文字节。 */
    public class AuthenticationFailed(
        public val path: String,
    ) : BackupFormatException("MAC verification failed (wrong passphrase or tampered file): $path")

    /** 文件在预期结束位置之前就没有字节了。 */
    public class Truncated(
        public val path: String,
    ) : BackupFormatException("Unexpected end of file, backup is truncated: $path")

    /** manifest 的 schema 名与调用方声明的不一致——防止把别的 app 的备份包灌进来。 */
    public class SchemaMismatch(
        public val expected: String,
        public val found: String,
    ) : BackupFormatException("Backup schema mismatch: expected=$expected found=$found")

    /** manifest 的 `manifestVersion` 是未知值。 */
    public class UnsupportedManifestVersion(
        public val found: Int,
        public val supportedRange: IntRange,
    ) : BackupFormatException("Unsupported manifestVersion=$found (supported: $supportedRange)")
}
