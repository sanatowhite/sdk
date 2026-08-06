package io.sanato.appkit.feature.feedback

import android.os.Build
import io.sanato.appkit.core.common.AppBuildInfo

/**
 * 把 [AppBuildInfo] 打进日志——库模块拿不到消费方的 `BuildConfig`,`AppBuildInfo`
 * 已经是"零配置从 PackageManager 读取"的等价物(见 core-common 说明),
 * `gitSha`/`buildTimeMillis` 消费方可选覆盖。
 */
object DeviceInfoProvider {
    fun snapshot(buildInfo: AppBuildInfo): String =
        buildString {
            appendLine("App: ${buildInfo.applicationId} ${buildInfo.versionName} (${buildInfo.versionCode})")
            appendLine("Git SHA: ${buildInfo.gitSha}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        }
}
