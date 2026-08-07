package io.sanato.appkit.backup.drive

/**
 * Google Drive 访问令牌来源。刻意不暴露任何 `com.google.android.gms.*` 类型——
 * 消费方永远不需要在自己代码里写出 GMS 类型名。[GmsDriveAuthorizer] 是它的默认实现，
 * 但这个接口本身与 GMS 无关，方便测试时用假实现替换。
 */
public fun interface DriveTokenProvider {
    /** 返回一个当前有效的 OAuth access token；过期/未授权时应抛异常而不是返回空字符串。 */
    public suspend fun currentAccessToken(): String
}
