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
    public val isNoConnectivity: Boolean = false,
) {
    public val isSuccess: Boolean get() = accessToken != null
    public val needsConsent: Boolean get() = consentIntentSender != null

    public companion object {
        public fun success(token: String): DriveAuthResult = DriveAuthResult(token, null, null)

        public fun consentRequired(sender: IntentSender): DriveAuthResult = DriveAuthResult(null, sender, null)

        public fun failure(message: String): DriveAuthResult = DriveAuthResult(null, null, message)

        /**
         * 设备当前没有可用网络——跟 [failure] 分开是为了让调用方不用做字符串匹配就能精确
         * 区分"这是断网"和"授权本身出了别的问题"，分别展示不同的用户提示。
         */
        public fun noConnectivity(): DriveAuthResult =
            DriveAuthResult(null, null, "no network connectivity", isNoConnectivity = true)
    }
}
