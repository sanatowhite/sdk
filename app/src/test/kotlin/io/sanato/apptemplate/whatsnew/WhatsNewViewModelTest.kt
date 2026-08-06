package io.sanato.apptemplate.whatsnew

import app.cash.turbine.test
import io.sanato.apptemplate.about.BuildInfo
import io.sanato.apptemplate.core.data.FakeUserSettingsRepository
import io.sanato.apptemplate.core.data.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 覆盖 Phase 7 实测中发现的那个真实 bug 的回归:`shouldShow` 在 DataStore
 * 真实值到达前必须是 null(不知道),而不是可以被误判成 false 的默认值——
 * 否则调用方会在真实值到达前抢先调用 markSeen(),永久压制这个弹窗。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WhatsNewViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // shouldShowWhatsNew 是纯函数,跟 BuildInfo.versionCode 的真实取值无关——
    // 用任意 lastSeen/current 组合直接覆盖"升级"这类当前仓库版本号(1)还
    // 无法真实模拟的场景。
    @Test
    fun `fresh install (lastSeen=0) never shows regardless of current version`() {
        assertFalse(shouldShowWhatsNew(lastSeenVersionCode = 0, currentVersionCode = 1))
        assertFalse(shouldShowWhatsNew(lastSeenVersionCode = 0, currentVersionCode = 5))
    }

    @Test
    fun `upgrade from an older seen version shows`() {
        assertTrue(shouldShowWhatsNew(lastSeenVersionCode = 2, currentVersionCode = 5))
    }

    @Test
    fun `already on the latest seen version does not show`() {
        assertFalse(shouldShowWhatsNew(lastSeenVersionCode = 5, currentVersionCode = 5))
    }

    @Test
    fun `fresh install does not show the sheet`() =
        runTest {
            val repository = FakeUserSettingsRepository(UserSettings(lastSeenWhatsNewVersionCode = 0))
            val viewModel = WhatsNewViewModel(repository)

            viewModel.shouldShow.test {
                assertNull(awaitItem())
                assertEquals(false, awaitItem())
            }
        }

    @Test
    fun `markSeen persists the current version code`() =
        runTest {
            val repository = FakeUserSettingsRepository(UserSettings(lastSeenWhatsNewVersionCode = 0))
            val viewModel = WhatsNewViewModel(repository)

            viewModel.markSeen()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(BuildInfo.versionCode, repository.currentValue().lastSeenWhatsNewVersionCode)
        }
}
