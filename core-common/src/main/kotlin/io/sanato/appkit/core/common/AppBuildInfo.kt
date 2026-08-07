package io.sanato.appkit.core.common

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用构建信息,零配置从 `PackageManager` 读取——不依赖消费方自己的
 * `BuildConfig`(库模块拿不到消费方的 `BuildConfig`,那是编译期生成在消费方
 * 自己包名下的类)。`gitSha`/`buildTimeMillis` 默认空,消费方想要这两项可以
 * 通过 `:core-common-hilt` 的 `AppBuildInfoOverride` 可选绑定覆盖(见该模块)。
 */
data class AppBuildInfo(
    val applicationId: String,
    val appLabel: String,
    val versionName: String,
    val versionCode: Long,
    val gitSha: String = "",
    val buildTimeMillis: Long = 0L,
) {
    /** `buildTimeMillis` 为 0(默认值/debug 构建)时返回 `"-"`。 */
    fun formattedBuildTime(): String {
        if (buildTimeMillis == 0L) return "-"
        val date = Date(buildTimeMillis)
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)
    }

    companion object {
        fun fromPackageManager(context: Context): AppBuildInfo {
            val packageManager = context.packageManager
            val packageName = context.packageName
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(context.applicationInfo).toString()

            @Suppress("DEPRECATION")
            val versionCode =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }
            return AppBuildInfo(
                applicationId = packageName,
                appLabel = label,
                versionName = packageInfo.versionName ?: "",
                versionCode = versionCode,
            )
        }
    }
}
