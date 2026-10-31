package io.sanato.appkit.safemode

/**
 * 安全模式暗号：在页面顶部下拉超过刷新触发点，按住 ≥ [holdMs]。满足即触发（[consumeIfSatisfied]，
 * 由定时器在到点时调用，不必等松手）；[onUp] 是定时器迟到时的兜底。只越过触发点就松手的普通下拉
 * 刷新不会触发。纯逻辑，时间由调用方传入（MotionEvent.eventTime / SystemClock.uptimeMillis），便于单测。
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

    /** 按住已满足则触发并结束本次手势（之后的 move/up 不会再触发），返回是否触发。 */
    fun consumeIfSatisfied(t: Long): Boolean {
        if (!isHoldSatisfied(t)) return false
        onCancel()
        return true
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
