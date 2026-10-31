package io.sanato.appkit.safemode

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.fragment.app.FragmentActivity
import java.util.Locale

/**
 * 安全模式（伪装启动页）的唯一入口。
 *
 * 开启后桌面入口换成宿主提供的伪装图标，打开是一个网页（[SafeModeConfig.decoyUrl]）；
 * 在网页顶部下拉、按住 [holdDurationMs] 后松手，再通过指纹/锁屏密码验证，才进入
 * [SafeModeConfig.homeActivity]。App 退到后台（可设宽限期 [gracePeriodMs]）或锁屏
 * （可关 [isLockOnScreenOff]）后重新上锁。接入步骤见模块 README。
 */
object SafeMode {
    /** 暗号默认按住时长（毫秒）。 */
    const val DEFAULT_HOLD_DURATION_MS: Long = SafeModeSettings.DEFAULT_HOLD_MS

    /** 推荐给设置页做单选的档位（毫秒，升序，含默认值）。 */
    @JvmField
    val HOLD_DURATION_OPTIONS_MS: List<Long> = SafeModeSettings.HOLD_OPTIONS_MS

    /** 在 `Application.onCreate()` 里调用一次。 */
    @JvmStatic
    fun install(
        application: Application,
        config: SafeModeConfig,
    ) = SafeModeGate.install(application, config)

    @JvmStatic
    fun isEnabled(context: Context): Boolean = SafeModePrefs.isEnabled(context)

    /**
     * 写入开关并切换桌面入口。调用前应先用 [authenticate] 验证身份。
     *
     * 开启时**立即上锁并关闭所有真实页面**（App 回到桌面），下次只能从伪装入口进——
     * 让用户马上确认伪装生效，也避免开启后一直停留在解锁状态。[SafeModeConfig.killProcessOnEnable]
     * 为 true 时随后结束进程。
     */
    @JvmStatic
    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        val launcher = checkNotNull(SafeModeGate.launcher) { "call SafeMode.install() first" }
        SafeModePrefs.setEnabled(context, enabled)
        launcher.apply(context, decoy = enabled)
        if (enabled) SafeModeGate.lockNow()
    }

    /** 当前暗号按住时长（毫秒）。 */
    @JvmStatic
    fun holdDurationMs(context: Context): Long = SafeModePrefs.holdMs(context)

    /** 设置暗号按住时长，超出 300–5000ms 会被夹紧。下次打开伪装页时生效。 */
    @JvmStatic
    fun setHoldDurationMs(
        context: Context,
        ms: Long,
    ) = SafeModePrefs.setHoldMs(context, ms)

    /** 退后台宽限期默认值（毫秒）：0 = 立即上锁。 */
    const val DEFAULT_GRACE_PERIOD_MS: Long = SafeModeSettings.DEFAULT_GRACE_MS

    /** 推荐给设置页做单选的宽限期档位（毫秒，升序，含默认值 0）：立即/30 秒/1/5/15/30 分钟。 */
    @JvmField
    val GRACE_PERIOD_OPTIONS_MS: List<Long> = SafeModeSettings.GRACE_OPTIONS_MS

    /** 退到后台后多久重新上锁（毫秒），0 = 立即。 */
    @JvmStatic
    fun gracePeriodMs(context: Context): Long = SafeModePrefs.graceMs(context)

    /**
     * 设置退后台宽限期，超出 0–30 分钟会被夹紧。宽限期内回到 App 不需要再走伪装页；
     * 进程被杀则一律上锁。下次退后台时生效。
     */
    @JvmStatic
    fun setGracePeriodMs(
        context: Context,
        ms: Long,
    ) = SafeModePrefs.setGraceMs(context, ms)

    /** 宽限期的展示文案，如 0 → "立即"，30000 → "30 秒"，300000 → "5 分钟"（跟随系统语言）。 */
    @JvmStatic
    fun formatGracePeriod(
        context: Context,
        ms: Long,
    ): String =
        when (val label = SafeModeSettings.graceLabelParts(ms)) {
            SafeModeSettings.GraceLabel.Immediately -> context.getString(R.string.safemode_grace_immediately)
            is SafeModeSettings.GraceLabel.Seconds -> context.getString(R.string.safemode_grace_seconds, label.value)
            is SafeModeSettings.GraceLabel.Minutes -> context.getString(R.string.safemode_grace_minutes, label.value)
        }

    /** 锁屏时是否立即上锁（默认 true）。关闭后锁屏按普通退后台处理，适用宽限期。 */
    @JvmStatic
    fun isLockOnScreenOff(context: Context): Boolean = SafeModePrefs.lockOnScreenOff(context)

    @JvmStatic
    fun setLockOnScreenOff(
        context: Context,
        enabled: Boolean,
    ) = SafeModePrefs.setLockOnScreenOff(context, enabled)

    /**
     * App 主动跳到外部之前调用（分享、外部播放器、系统选文件、安装更新等），这一次回来不上锁。
     * 登记后 5 秒内没有真的离开就作废；离开超过 10 分钟（宽限期更长时按宽限期）或中途锁屏上锁，
     * 回来照样上锁。
     */
    @JvmStatic
    fun allowExternalTrip() = SafeModeGate.allowExternalTrip()

    /** 宿主的全局 Activity 处理（如统一加 FLAG_SECURE）用来跳过伪装页。 */
    @JvmStatic
    fun isDecoy(activity: Activity): Boolean = activity is DecoyActivity

    /** 设备上是否有指纹/人脸或锁屏密码可用于验证。都没有时宿主应禁止开启安全模式。 */
    @JvmStatic
    fun canAuthenticate(context: Context): Boolean = SafeModeAuthenticator.canAuthenticate(context)

    /** 弹出系统验证框（标题「验证身份」）。失败、取消都走 [onFailOrCancel]。 */
    @JvmStatic
    @JvmOverloads
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailOrCancel: () -> Unit = {},
    ) = SafeModeAuthenticator.authenticate(activity, onSuccess, onFailOrCancel)

    /** 把毫秒格式化成设置页展示用的秒数，如 1200 → "1.2"，2000 → "2"。 */
    @JvmStatic
    fun formatSeconds(ms: Long): String {
        val text = String.format(Locale.US, "%.1f", ms / 1000.0)
        return text.removeSuffix(".0")
    }

    /** 开启前说明框的现成文案（含当前按住时长），宿主可直接用作 AlertDialog 的 message。 */
    @JvmStatic
    fun enableExplanation(
        context: Context,
        decoyLabel: CharSequence,
    ): String =
        context.getString(
            R.string.safemode_enable_explanation,
            decoyLabel,
            formatSeconds(holdDurationMs(context)),
        )
}
