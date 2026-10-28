package io.sanato.appkit.safemode

import android.app.Activity

/**
 * 宿主接入安全模式的配置，传给 [SafeMode.install]。
 *
 * 两个 alias 是宿主 manifest 里 `<activity-alias>` 的**完整类名**（按宿主 namespace 拼，
 * 不是 applicationId——debug 包加了 `.debug` 后缀时类名不变），SDK 用运行时
 * `context.packageName` 组 ComponentName，所以 debug/release 包都能定位到。
 *
 * @property homeActivity 解锁后进入的真实首页（通常就是 [realLauncherAlias] 指向的那个 Activity）。
 * @property realLauncherAlias 指向 [homeActivity] 的 alias，manifest 里 `android:enabled="true"`。
 * @property decoyLauncherAlias 指向 [DecoyActivity] 的 alias，manifest 里 `android:enabled="false"`。
 * @property decoyUrl 伪装页加载的网址，只允许 http/https。
 */
class SafeModeConfig(
    val homeActivity: Class<out Activity>,
    val realLauncherAlias: String,
    val decoyLauncherAlias: String,
    val decoyUrl: String = DEFAULT_DECOY_URL,
) {
    companion object {
        const val DEFAULT_DECOY_URL = "https://m.baidu.com"
    }
}
