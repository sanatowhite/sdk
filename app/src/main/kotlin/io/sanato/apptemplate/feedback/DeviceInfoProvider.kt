package io.sanato.apptemplate.feedback

import android.os.Build
import io.sanato.apptemplate.BuildConfig

/**
 * 把 `BuildConfig.APPLICATION_ID` 打进日志——debug/release 的 applicationId
 * 不同(`.debug` 后缀),方便从反馈邮件直接分清是哪个包上报的问题。
 */
object DeviceInfoProvider {
    fun snapshot(): String =
        buildString {
            appendLine("App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Git SHA: ${BuildConfig.GIT_SHA}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        }
}
