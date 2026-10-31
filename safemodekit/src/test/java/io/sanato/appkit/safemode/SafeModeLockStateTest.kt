package io.sanato.appkit.safemode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeModeLockStateTest {
    private var clock = 0L

    private var grace = 0L

    private fun state() = SafeModeLockState(graceMs = { grace }, now = { clock })

    private fun unlocked() = state().apply { unlock() }

    @Test
    fun startsLocked() {
        assertTrue(state().isLocked)
    }

    @Test
    fun backgroundLocksAfterUnlock() {
        val s = state()
        s.unlock()
        assertFalse(s.isLocked)
        assertTrue(s.onAppBackgrounded())
        assertTrue(s.isLocked)
    }

    @Test
    fun backgroundWhenAlreadyLockedReportsNoChange() {
        val s = state()
        assertFalse(s.onAppBackgrounded())
        assertTrue(s.isLocked)
    }

    @Test
    fun externalTripKeepsUnlockedAcrossBackground() {
        val s = state()
        s.unlock()
        s.allowExternalTrip()
        assertFalse(s.onAppBackgrounded())
        clock += 60_000
        assertFalse(s.onAppForegrounded())
        assertFalse(s.isLocked)
    }

    @Test
    fun tripIsOneShot() {
        val s = state()
        s.unlock()
        s.allowExternalTrip()
        s.onAppBackgrounded()
        s.onAppForegrounded()
        s.onActivityResumed()
        assertTrue(s.onAppBackgrounded()) // 第二次退后台不再放行
    }

    @Test
    fun tripExpiresAfterTenMinutes() {
        val s = state()
        s.unlock()
        s.allowExternalTrip()
        s.onAppBackgrounded()
        clock += SafeModeLockState.TRIP_MAX_MS + 1
        assertTrue(s.onAppForegrounded())
        assertTrue(s.isLocked)
    }

    @Test
    fun tripExactlyAtLimitStillAllowed() {
        val s = state()
        s.unlock()
        s.allowExternalTrip()
        s.onAppBackgrounded()
        clock += SafeModeLockState.TRIP_MAX_MS
        assertFalse(s.onAppForegrounded())
    }

    @Test
    fun screenOffCancelsTripAndLocks() {
        val s = state()
        s.unlock()
        s.allowExternalTrip()
        s.onAppBackgrounded()
        assertTrue(s.onScreenOff())
        assertTrue(s.isLocked)
        assertFalse(s.onAppForegrounded()) // 已锁，无需再次跳转
    }

    @Test
    fun resumeWithoutBackgroundClearsTrip() {
        // 分享面板只让 Activity pause、不 stop：回来时只有 resumed
        val s = state()
        s.unlock()
        s.allowExternalTrip()
        s.onActivityResumed()
        assertTrue(s.onAppBackgrounded()) // 之后按 Home 必须上锁
    }

    @Test
    fun allowExternalTripIgnoredWhileLocked() {
        val s = state()
        s.allowExternalTrip()
        s.unlock()
        assertTrue(s.onAppBackgrounded())
    }

    @Test
    fun tripNotFollowedByBackgroundWithinArmWindowIsDropped() {
        // 验证框/普通 Dialog 不会让 Activity pause，没有 resume 来清标记：
        // 登记后迟迟没有真正离开，之后按 Home 必须照常上锁
        val s = state()
        s.unlock()
        s.allowExternalTrip()
        clock += SafeModeLockState.TRIP_ARM_MS + 1
        assertTrue(s.onAppBackgrounded())
        assertTrue(s.isLocked)
    }

    @Test
    fun tripFollowedByBackgroundWithinArmWindowIsKept() {
        val s = state()
        s.unlock()
        s.allowExternalTrip()
        clock += SafeModeLockState.TRIP_ARM_MS
        assertFalse(s.onAppBackgrounded())
        assertFalse(s.isLocked)
    }

    @Test
    fun backgroundDuringAuthenticationDoesNotLock() {
        val s = unlocked()
        s.beginAuthentication()
        clock += 30_000 // 用户在指纹框上停留很久才点「使用密码」
        assertFalse(s.onAppBackgrounded())
        assertFalse(s.isLocked)
    }

    @Test
    fun backgroundAfterAuthenticationEndedLocks() {
        val s = unlocked()
        s.beginAuthentication()
        s.endAuthentication()
        assertTrue(s.onAppBackgrounded())
        assertTrue(s.isLocked)
    }

    @Test
    fun screenOffDuringAuthenticationStillLocks() {
        val s = unlocked()
        s.beginAuthentication()
        assertTrue(s.onScreenOff())
        // 锁屏同时作废验证中状态，之后退后台照常处理
        assertFalse(s.onAppBackgrounded())
        assertTrue(s.isLocked)
    }

    @Test
    fun lockNowLocksAndDropsTripAndAuthentication() {
        val s = unlocked()
        s.allowExternalTrip()
        s.beginAuthentication()
        assertTrue(s.lockNow())
        assertTrue(s.isLocked)
        // 放行和验证中状态都已作废：再解锁后退后台照常上锁
        s.unlock()
        assertTrue(s.onAppBackgrounded())
    }

    @Test
    fun lockNowWhenAlreadyLockedReportsNoChange() {
        assertFalse(state().lockNow())
    }

    // ── 退后台宽限期 ──

    @Test
    fun graceKeepsUnlockedWhenReturningInTime() {
        grace = 60_000
        val s = unlocked()
        assertFalse(s.onAppBackgrounded()) // 只记时间，不关闭页面
        assertFalse(s.isLocked)
        clock += 59_999
        assertFalse(s.onAppForegrounded())
        assertFalse(s.isLocked)
    }

    @Test
    fun graceLocksWhenReturningAfterItElapsed() {
        grace = 60_000
        val s = unlocked()
        s.onAppBackgrounded()
        clock += 60_000
        assertTrue(s.onAppForegrounded())
        assertTrue(s.isLocked)
    }

    @Test
    fun graceRestartsOnEachBackground() {
        grace = 30_000
        val s = unlocked()
        s.onAppBackgrounded()
        clock += 20_000
        assertFalse(s.onAppForegrounded())
        clock += 60_000 // 前台停留不计入
        s.onAppBackgrounded()
        clock += 20_000
        assertFalse(s.onAppForegrounded())
        assertFalse(s.isLocked)
    }

    @Test
    fun zeroGraceLocksImmediatelyOnBackground() {
        grace = 0
        val s = unlocked()
        assertTrue(s.onAppBackgrounded())
        assertTrue(s.isLocked)
    }

    @Test
    fun graceChangedWhileBackgroundedUsesNewValueOnReturn() {
        grace = 15 * 60_000L
        val s = unlocked()
        s.onAppBackgrounded()
        clock += 2 * 60_000L
        grace = 60_000 // 期间在别处改短了
        assertTrue(s.onAppForegrounded())
    }

    @Test
    fun freshStateIsLockedSoProcessDeathAlwaysLocks() {
        // 进程被杀后重建：不管宽限期多长，冷启动都已上锁
        grace = 30 * 60_000L
        val s = state()
        assertTrue(s.isLocked)
        assertFalse(s.onAppForegrounded())
        assertTrue(s.isLocked)
    }

    @Test
    fun screenOffWithLockImmediatelyCancelsPendingGrace() {
        grace = 5 * 60_000L
        val s = unlocked()
        s.onAppBackgrounded()
        assertTrue(s.onScreenOff(lockImmediately = true))
        assertTrue(s.isLocked)
        assertFalse(s.onAppForegrounded())
    }

    @Test
    fun screenOffWithoutLockImmediatelyLeavesGraceToBackground() {
        grace = 5 * 60_000L
        val s = unlocked()
        assertFalse(s.onScreenOff(lockImmediately = false))
        assertFalse(s.isLocked)
        assertFalse(s.onAppBackgrounded()) // 锁屏导致 Activity stop：按宽限期处理
        clock += 60_000
        assertFalse(s.onAppForegrounded())
        assertFalse(s.isLocked)
    }

    @Test
    fun screenOffWithoutLockImmediatelyStillLocksAfterGrace() {
        grace = 30_000
        val s = unlocked()
        s.onScreenOff(lockImmediately = false)
        s.onAppBackgrounded()
        clock += 30_000
        assertTrue(s.onAppForegrounded())
    }

    @Test
    fun screenOffWithoutLockImmediatelyKeepsTrip() {
        val s = unlocked()
        s.allowExternalTrip()
        s.onAppBackgrounded()
        assertFalse(s.onScreenOff(lockImmediately = false))
        clock += 60_000
        assertFalse(s.onAppForegrounded())
        assertFalse(s.isLocked)
    }

    @Test
    fun tripLimitExtendsToLongerGrace() {
        grace = 30 * 60_000L
        val s = unlocked()
        s.allowExternalTrip()
        s.onAppBackgrounded()
        clock += 15 * 60_000L // 超过 TRIP_MAX_MS，但仍在宽限期内
        assertFalse(s.onAppForegrounded())
        assertFalse(s.isLocked)
    }

    @Test
    fun tripStillWorksWithGrace() {
        grace = 30_000
        val s = unlocked()
        s.allowExternalTrip()
        assertFalse(s.onAppBackgrounded())
        clock += 5 * 60_000L // 超过宽限期但在外跳上限内
        assertFalse(s.onAppForegrounded())
        assertFalse(s.isLocked)
    }

    @Test
    fun lockNowCancelsPendingGrace() {
        grace = 60_000
        val s = unlocked()
        s.onAppBackgrounded()
        assertTrue(s.lockNow())
        s.unlock()
        assertFalse(s.onAppForegrounded()) // 解锁后旧的离开时间已作废
    }
}
