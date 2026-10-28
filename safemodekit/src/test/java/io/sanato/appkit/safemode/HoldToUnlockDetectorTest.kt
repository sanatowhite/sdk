package io.sanato.appkit.safemode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldToUnlockDetectorTest {

    private val trigger = 300f
    private val hold = 1200L
    private fun detector(holdMs: Long = hold) = HoldToUnlockDetector(trigger, holdMs)

    @Test
    fun holdPastTriggerForFullDurationUnlocksOnUp() {
        val d = detector()
        d.onDown(atTop = true, y = 100f, t = 0)
        d.onMove(y = 450f, t = 100)          // 过触发点，开始计时
        assertEquals(100L, d.holdStartTime)
        assertTrue(d.onUp(t = 100 + hold))
    }

    @Test
    fun releaseBeforeHoldDurationDoesNotUnlock() {
        val d = detector()
        d.onDown(true, 100f, 0)
        d.onMove(450f, 100)
        assertFalse(d.onUp(100 + 1100))
    }

    @Test
    fun notPastTriggerNeverStartsTimer() {
        val d = detector()
        d.onDown(true, 100f, 0)
        d.onMove(350f, 100)                  // dy = 250 < 300
        assertNull(d.holdStartTime)
        assertFalse(d.onUp(5000))
    }

    @Test
    fun smallJitterAboveTriggerKeepsTimer() {
        val d = detector()
        d.onDown(true, 100f, 0)
        d.onMove(450f, 100)
        d.onMove(430f, 600)                  // 仍 ≥ 触发点
        d.onMove(460f, 900)
        assertEquals(100L, d.holdStartTime)
        assertTrue(d.onUp(1300))
    }

    @Test
    fun pushingBackBelowTriggerResetsTimer() {
        val d = detector()
        d.onDown(true, 100f, 0)
        d.onMove(450f, 100)
        d.onMove(300f, 800)                  // 推回到触发点以下：作废
        assertNull(d.holdStartTime)
        d.onMove(450f, 900)                  // 重新过触发点：重新计时
        assertEquals(900L, d.holdStartTime)
        assertFalse(d.onUp(1400))
        d.onDown(true, 100f, 2000)
        d.onMove(450f, 2100)
        assertTrue(d.onUp(2100 + 1200))
    }

    @Test
    fun downWhenNotAtTopIsIgnored() {
        val d = detector()
        d.onDown(atTop = false, y = 100f, t = 0)
        d.onMove(900f, 100)
        assertNull(d.holdStartTime)
        assertFalse(d.onUp(5000))
    }

    @Test
    fun isHoldSatisfiedWorksWithoutMoveEvents() {
        val d = detector()
        d.onDown(true, 100f, 0)
        d.onMove(450f, 100)
        assertFalse(d.isHoldSatisfied(1299))
        assertTrue(d.isHoldSatisfied(1300))  // 手指完全静止也能判定（供定时器震动）
    }

    @Test
    fun cancelResetsEverything() {
        val d = detector()
        d.onDown(true, 100f, 0)
        d.onMove(450f, 100)
        d.onCancel()
        assertNull(d.holdStartTime)
        assertFalse(d.onUp(5000))
    }

    @Test
    fun upResetsSoNextGestureStartsFresh() {
        val d = detector()
        d.onDown(true, 100f, 0)
        d.onMove(450f, 100)
        assertTrue(d.onUp(1300))
        assertFalse(d.onUp(9000))            // 没有新的 down，不能再次触发
    }

    @Test
    fun customHoldDurationIsRespected() {
        val d = detector(holdMs = 500)
        d.onDown(true, 100f, 0)
        d.onMove(450f, 100)
        assertFalse(d.isHoldSatisfied(100 + 499))
        assertTrue(d.onUp(100 + 500))
    }

    @Test
    fun longerHoldDurationNeedsLongerPress() {
        val d = detector(holdMs = 3000)
        d.onDown(true, 100f, 0)
        d.onMove(450f, 100)
        assertFalse(d.onUp(100 + 2999))
    }
}
