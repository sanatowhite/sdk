package io.sanato.appkit.backup.format

/**
 * 不解密即可读出的头部视图，供诊断/UI 展示用。
 *
 * ⚠️ 契约：本对象的内容是【未认证】的——头部 CRC32 只挡意外损坏，不挡蓄意篡改，
 * MAC 校验只在 [BackupCodec.unseal] 内部发生。调用方**不得**基于 [inspect] 的结果做
 * 安全决策（例如"头说是 manifest-only 所以跳过媒体校验"）；任何依赖头部字段的判断
 * 都必须在 `unseal()` 成功之后重新做一遍。
 */
public class BackupContainerInfo(
    public val formatVersion: Int,
    public val cipherId: Int,
    public val macId: Int,
    public val kdfId: Int,
    public val kdfIterations: Int,
    public val contentType: Int,
    public val payloadProfile: Int,
    public val createdAtMillis: Long,
    public val plaintextLength: Long,
    public val producer: String,
    public val isLegacyFormat: Boolean,
)
