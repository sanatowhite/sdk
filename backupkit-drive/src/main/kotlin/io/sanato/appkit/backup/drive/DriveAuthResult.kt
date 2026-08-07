package io.sanato.appkit.backup.drive

import android.content.IntentSender

/**
 * 一次授权尝试的结果——三选一：成功拿到 token；需要用户完成同意（宿主 Activity 用
 * [consentIntentSender] 发起 `startIntentSenderForResult`）；失败。刻意不携带任何
 * `com.google.android.gms.*` 类型。
 */
public class DriveAuthResult private constructor(
    public val accessToken: String?,
    public val consentIntentSender: IntentSender?,
    public val errorMessage: String?,
) {
    public val isSuccess: Boolean get() = accessToken != null
    public val needsConsent: Boolean get() = consentIntentSender != null

    public companion object {
        public fun success(token: String): DriveAuthResult = DriveAuthResult(token, null, null)

        public fun consentRequired(sender: IntentSender): DriveAuthResult = DriveAuthResult(null, sender, null)

        public fun failure(message: String): DriveAuthResult = DriveAuthResult(null, null, message)
    }
}
