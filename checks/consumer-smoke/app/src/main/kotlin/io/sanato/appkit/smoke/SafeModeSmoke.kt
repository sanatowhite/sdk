package io.sanato.appkit.smoke

import android.app.Activity
import android.content.Context
import androidx.fragment.app.FragmentActivity
import io.sanato.appkit.safemode.DecoyActivity
import io.sanato.appkit.safemode.SafeMode
import io.sanato.appkit.safemode.SafeModeConfig

/**
 * 逐个引用 `:safemodekit` 的公开入口，只做编译期验证——最容易判错的 `api`/`implementation`
 * 边界是 [SafeMode.authenticate] 签名里的 [FragmentActivity]（`:safemodekit` 对
 * `androidx.biometric` 用 `api` 的唯一理由）。从不调用 `install`：这个 smoke app 没有声明
 * 伪装入口的 activity-alias。
 */
object SafeModeSmoke {
    val config =
        SafeModeConfig(
            homeActivity = MainActivity::class.java,
            realLauncherAlias = "io.sanato.appkit.smoke.LauncherReal",
            decoyLauncherAlias = "io.sanato.appkit.smoke.LauncherDecoy",
        )

    val decoyActivity: Class<out Activity> = DecoyActivity::class.java

    fun describe(context: Context): String =
        "enabled=${SafeMode.isEnabled(context)} " +
            "hold=${SafeMode.formatSeconds(SafeMode.holdDurationMs(context))}s " +
            "options=${SafeMode.HOLD_DURATION_OPTIONS_MS}"

    fun verify(activity: FragmentActivity) {
        if (SafeMode.canAuthenticate(activity)) SafeMode.authenticate(activity, onSuccess = {})
    }
}
