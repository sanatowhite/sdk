package io.sanato.appkit.backup.format

/**
 * SBK1 容器头部里各字段的取值注册表。新增算法只需要在这里加一个常量、在
 * [Sbk1BackupCodec] 里加一个 `when` 分支——不需要 bump [FORMAT_VERSION]。
 * 见 `:backupkit` README 的容器格式规范。
 */
public object BackupAlgorithms {
    public const val FORMAT_VERSION: Int = 1

    /** AES-256-CTR，ivLength=16（完整 128-bit 计数器块）。GCM 因 Conscrypt 无法真流式、
     * 大文件必 OOM，刻意不用；编号 2 保留给它，永不实现。 */
    public const val CIPHER_AES256_CTR: Int = 1

    public const val MAC_HMAC_SHA256: Int = 1

    public const val KDF_PBKDF2_HMAC_SHA256: Int = 1

    public const val CONTENT_ARCHIVE_ZIP: Int = 1
    public const val CONTENT_OPAQUE_BLOB: Int = 2

    public const val PROFILE_FULL: Int = 1
    public const val PROFILE_MANIFEST_ONLY: Int = 2
    public const val PROFILE_MEDIA: Int = 3

    public const val DEFAULT_PBKDF2_ITERATIONS: Int = 120_000

    internal const val MAGIC = "SBK1"
    internal const val SALT_SIZE = 16
    internal const val IV_SIZE = 16
    internal const val MAC_SIZE = 32
    internal const val KEY_SIZE = 32
    internal const val HEADER_FIXED_SIZE = 48
    internal const val HEADER_ALIGNMENT = 16
}
