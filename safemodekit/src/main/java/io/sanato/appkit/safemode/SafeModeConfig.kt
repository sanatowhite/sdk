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
 * @property killProcessOnEnable 开启安全模式后是否直接结束进程（先关闭所有页面再杀）。会中断
 *   进程内正在进行的工作（如下载），宿主需自行保证可恢复；默认 false，只关闭页面。
 * @property keepAliveWhile 宿主返回 true 时，锁屏/退后台**暂不上锁**（不关真实页面）。
 *
 *   典型用途：安全模式下用户显式打开了"允许锁屏/后台播放"，视频正在放——这时候锁屏把播放页
 *   finish 掉就等于"一锁屏视频就没了"。宿主在这里回答"现在是不是不能锁"，SDK 只负责推迟；
 *   一旦返回 false，宿主必须调 [SafeMode.onKeepAliveRequiredChanged] 让 SDK 按正常规则补上锁
 *   （否则会一直停在解锁状态）。
 *
 *   这个回调会在系统生命周期回调里被调用，必须是**纯内存判断**，不要做 IO。
 *   默认 `{ false }`，即老的"锁屏/退后台立即上锁"行为不变。
 */
class SafeModeConfig(
    val homeActivity: Class<out Activity>,
    val realLauncherAlias: String,
    val decoyLauncherAlias: String,
    val decoyUrl: String = DEFAULT_DECOY_URL,
    val killProcessOnEnable: Boolean = false,
    val keepAliveWhile: () -> Boolean = { false },
) {
    companion object {
        const val DEFAULT_DECOY_URL = "https://m.baidu.com"
    }
}
