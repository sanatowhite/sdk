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

    fun unlock() {
        isLocked = false
    }

    fun allowExternalTrip() {
        if (!isLocked) tripStartedAt = now()
    }

    fun onAppBackgrounded(): Boolean {
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

    fun onScreenOff(): Boolean {
        tripStartedAt = null
        val changed = !isLocked
        isLocked = true
        return changed
    }

    companion object {
        const val TRIP_MAX_MS = 10 * 60 * 1000L
        const val TRIP_ARM_MS = 5_000L
    }
}
