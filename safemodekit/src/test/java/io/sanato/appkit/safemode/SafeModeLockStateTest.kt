package io.sanato.appkit.safemode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeModeLockStateTest {
    private var clock = 0L

    private fun state() = SafeModeLockState { clock }

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
}
