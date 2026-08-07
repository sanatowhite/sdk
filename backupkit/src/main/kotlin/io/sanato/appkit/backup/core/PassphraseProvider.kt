package io.sanato.appkit.backup.core

/**
 * 加密口令来源。backupkit 完全不关心口令是怎么来的——宿主可以从 app 签名派生、从用户
 * 输入的密码派生，或任何其它方案。backupkit 用完返回的字节数组后会尝试 `fill(0)` 清零
 * （`SecretKeySpec` 内部会拷贝一份且这份拷贝无法从外部擦除，这是 JCE 的固有限制，
 * 不是 backupkit 没做到位）。
 */
public fun interface PassphraseProvider {
    public suspend fun passphrase(): ByteArray
}
