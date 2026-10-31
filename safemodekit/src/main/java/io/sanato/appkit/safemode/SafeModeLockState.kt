package io.sanato.appkit.safemode

/**
 * 安全模式锁状态。纯逻辑，时钟注入（生产用 SystemClock.elapsedRealtime）。
 *
 * 退到后台时：宽限期（[graceMs]）为 0 立即上锁；否则只记下离开时间，下次回到前台时离开
 * ≥ 宽限期才上锁。进程被杀后状态重新从「已上锁」开始，所以冷启动一定走伪装页。
 *
 * 外跳放行（分享、外部播放器等）一次有效：登记后 [TRIP_ARM_MS] 内必须真的退到后台，
 * 放行最长 max([TRIP_MAX_MS], 宽限期)，锁屏上锁时作废。
 */
internal class SafeModeLockState(
    private val graceMs: () -> Long = { 0L },
    private val now: () -> Long,
) {
    var isLocked = true
        private set

    private var tripStartedAt: Long? = null

    /** 解锁状态下退到后台、宽限期内尚未回来的离开时间。 */
    private var backgroundedAt: Long? = null

    /** 验证框（API 30 以下会打开系统确认凭据页，让 App 进后台）正在进行中。 */
    private var authenticating = false

    fun unlock() {
        isLocked = false
        backgroundedAt = null
    }

    fun allowExternalTrip() {
        if (!isLocked) tripStartedAt = now()
    }

    fun beginAuthentication() {
        authenticating = true
    }

    fun endAuthentication() {
        authenticating = false
    }

    /** 返回 true 表示此刻由解锁变为上锁（调用方应关闭真实页面）。 */
    fun onAppBackgrounded(): Boolean {
        // 验证进行中离开前台的只可能是系统确认凭据页；不靠 TRIP_ARM_MS——用户可能在指纹框上
        // 停留很久才切到密码
        if (authenticating) return false
        val start = tripStartedAt
        // 登记后 TRIP_ARM_MS 内真的离开了才算外跳；否则（验证框、普通 Dialog 不会触发 resume
        // 来清标记）视为没跳出去，按普通退后台处理
        if (start != null && now() - start <= TRIP_ARM_MS) return false
        tripStartedAt = null
        if (isLocked) return false
        if (graceMs() > 0L) {
            backgroundedAt = now()
            return false
        }
        isLocked = true
        return true
    }

    /** 返回 true 表示此刻由解锁变为上锁（调用方应跳回伪装页）。 */
    fun onAppForegrounded(): Boolean {
        val grace = graceMs()
        val tripStart = tripStartedAt
        val leftAt = backgroundedAt
        tripStartedAt = null
        backgroundedAt = null
        if (isLocked) return false
        val expired =
            when {
                tripStart != null -> now() - tripStart > maxOf(TRIP_MAX_MS, grace)
                leftAt != null -> now() - leftAt >= grace
                else -> false
            }
        if (expired) isLocked = true
        return expired
    }

    fun onActivityResumed() {
        tripStartedAt = null
    }

    /**
     * 屏幕关闭。[lockImmediately] 为 true 时立即上锁（作废外跳放行和宽限期）；为 false 时不做
     * 任何事——Activity 随之 stop，按普通退后台走宽限期。
     */
    fun onScreenOff(lockImmediately: Boolean = true): Boolean = if (lockImmediately) lockNow() else false

    /** 立即上锁，作废外跳放行、宽限期和验证中状态。返回 true 表示由解锁变为上锁。 */
    fun lockNow(): Boolean {
        tripStartedAt = null
        backgroundedAt = null
        authenticating = false
        val changed = !isLocked
        isLocked = true
        return changed
    }

    companion object {
        const val TRIP_MAX_MS = 10 * 60 * 1000L
        const val TRIP_ARM_MS = 5_000L
    }
}
