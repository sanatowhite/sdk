package io.sanato.apptemplate.logging

import android.app.Application
import io.sanato.apptemplate.BuildConfig
import io.sanato.logkit.LogKit
import io.sanato.logkit.LogKitConfig

/**
 * 配置集中在一处,而不是散在 `attachBaseContext` 里——`LogKit.install()` 必须
 * 在 Hilt 之前跑,这个类因此也刻意不用任何 DI。
 */
object LogKitInstall {
    fun install(application: Application) {
        val config =
            LogKitConfig
                .Builder()
                // debug/staging 镜像到 logcat 方便本地看,release 关掉省 CPU。
                .setMirrorToLogcat(BuildConfig.DEBUG)
                .putMetadata("appVersionName", BuildConfig.VERSION_NAME)
                .putMetadata("gitSha", BuildConfig.GIT_SHA)
                .build()
        LogKit.install(application, config)
    }
}
