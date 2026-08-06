package io.sanato.apptemplate.about

import io.sanato.apptemplate.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** `GIT_SHA`/`BUILD_TIME_MILLIS` 来自 build-logic 的 `SanatoAndroidApplicationConventionPlugin`。 */
object BuildInfo {
    val gitSha: String get() = BuildConfig.GIT_SHA
    val versionName: String get() = BuildConfig.VERSION_NAME
    val versionCode: Int get() = BuildConfig.VERSION_CODE
    val applicationId: String get() = BuildConfig.APPLICATION_ID

    /** debug 构建固定是 0(见 convention plugin 里"只在 release 注入真实值"的说明)。 */
    fun formattedBuildTime(): String {
        if (BuildConfig.BUILD_TIME_MILLIS == 0L) return "-"
        val date = Date(BuildConfig.BUILD_TIME_MILLIS)
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)
    }
}
