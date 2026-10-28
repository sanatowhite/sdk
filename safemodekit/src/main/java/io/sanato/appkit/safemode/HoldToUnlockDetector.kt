package io.sanato.appkit.safemode

/**
 * 安全模式暗号：在页面顶部下拉超过刷新触发点，按住 ≥ [holdMs] 再松手。
 * 纯逻辑，时间由调用方传入（MotionEvent.eventTime / SystemClock.uptimeMillis），便于单测。
 */
internal class HoldToUnlockDetector(
    private val triggerPx: Float,
    val holdMs: Long,
) {
    private var tracking = false
    private var downY = 0f

    /** 本次越过触发点的时间；未越过或已作废时为 null。 */
    var holdStartTime: Long? = null
        private set

    fun onDown(
        atTop: Boolean,
        y: Float,
        t: Long,
    ) {
        tracking = atTop
        downY = y
        holdStartTime = null
    }

    fun onMove(
        y: Float,
        t: Long,
    ) {
        if (!tracking) return
        val pastTrigger = y - downY >= triggerPx
        if (pastTrigger) {
            if (holdStartTime == null) holdStartTime = t
        } else {
            holdStartTime = null
        }
    }

    fun isHoldSatisfied(t: Long): Boolean {
        val start = holdStartTime ?: return false
        return tracking && t - start >= holdMs
    }

    fun onUp(t: Long): Boolean {
        val satisfied = isHoldSatisfied(t)
        onCancel()
        return satisfied
    }

    fun onCancel() {
        tracking = false
        holdStartTime = null
    }
}
