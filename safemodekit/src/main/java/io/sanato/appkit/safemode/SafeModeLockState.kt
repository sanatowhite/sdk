package io.sanato.appkit.safemode

/**
 * 安全模式锁状态。纯逻辑，时钟注入（生产用 SystemClock.elapsedRealtime）。
 * 外跳放行（分享、外部播放器等）一次有效：登记后 [TRIP_ARM_MS] 内必须真的退到后台，
 * 放行最长 [TRIP_MAX_MS]，锁屏立即作废。
 */
internal class SafeModeLockState(
    private val now: () -> Long,
) {
    var isLocked = true
        private set

    private var tripStartedAt: Long? = null

    /** 验证框（API 30 以下会打开系统确认凭据页，让 App 进后台）正在进行中。 */
    private var authenticating = false

    fun unlock() {
        isLocked = false
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

    fun onAppBackgrounded(): Boolean {
        // 验证进行中离开前台的只可能是系统确认凭据页；不靠 TRIP_ARM_MS——用户可能在指纹框上
        // 停留很久才切到密码
        if (authenticating) return false
        val start = tripStartedAt
        // 登记后 TRIP_ARM_MS 内真的离开了才算外跳；否则（验证框、普通 Dialog 不会触发 resume
        // 来清标记）视为没跳出去，照常上锁
        if (start != null && now() - start <= TRIP_ARM_MS) return false
        tripStartedAt = null
        val changed = !isLocked
        isLocked = true
        return changed
    }

    fun onAppForegrounded(): Boolean {
        val start = tripStartedAt ?: return false
        tripStartedAt = null
        if (now() - start > TRIP_MAX_MS && !isLocked) {
            isLocked = true
            return true
        }
        return false
    }

    fun onActivityResumed() {
        tripStartedAt = null
    }

    fun onScreenOff(): Boolean = lockNow()

    /** 立即上锁，作废外跳放行和验证中状态。返回 true 表示由解锁变为上锁。 */
    fun lockNow(): Boolean {
        tripStartedAt = null
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
