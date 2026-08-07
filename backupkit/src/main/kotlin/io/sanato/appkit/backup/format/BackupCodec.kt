package io.sanato.appkit.backup.format

import io.sanato.appkit.backup.core.PassphraseProvider
import java.io.File

/** [BackupCodec.seal] 的可选参数。 */
public class SealOptions(
    public val contentType: Int = BackupAlgorithms.CONTENT_ARCHIVE_ZIP,
    public val payloadProfile: Int = BackupAlgorithms.PROFILE_FULL,
    public val kdfIterations: Int = BackupAlgorithms.DEFAULT_PBKDF2_ITERATIONS,
    /** 明文标识，如 "sanato-diary-android/2.4.0"。⚠️ 绝不能塞用户 ID/邮箱/账号——头部这一段是明文。 */
    public val producer: String = "",
)

/**
 * 容器编解码的公开契约。当前唯一实现是 [Sbk1BackupCodec]（写 SBK1，读 SBK1 + 只读的
 * legacy 格式）。
 *
 * ⚠️ SBK1 不支持从不可回溯的 `InputStream` 解密——所有 API 都以 [File] 为单位，因为
 * MAC 放在文件尾部（见容器格式规范"为什么 MAC 放尾部"），读侧需要能 seek。
 */
public interface BackupCodec {
    /** 不解密读取头部信息，用于诊断/UI/选择解码路径。见 [BackupContainerInfo] 的未认证契约。 */
    public fun inspect(sealedFile: File): BackupContainerInfo

    /** 加密封装。始终写当前版本的 SBK1 格式。 */
    public suspend fun seal(
        plainFile: File,
        outputFile: File,
        passphrase: PassphraseProvider,
        options: SealOptions = SealOptions(),
    )

    /**
     * 解密。按 [passphrases] 给出的顺序依次尝试，第一个能通过 MAC 校验的即为正确口令——
     * 这是宿主实现"先试新口令、失败再退回旧口令"策略的地方。
     *
     * 按文件头 magic 自动分派：SBK1 走当前实现；SDB1/SDB2/裸 zip 走只读的 legacy 解码器。
     */
    public suspend fun unseal(
        sealedFile: File,
        outputFile: File,
        passphrases: List<PassphraseProvider>,
    )
}
