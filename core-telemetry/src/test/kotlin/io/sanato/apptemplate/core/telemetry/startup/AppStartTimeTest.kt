package io.sanato.apptemplate.core.telemetry.startup

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `record()` 必须只在进程内第一次调用生效——`attachBaseContext` 只应该被系统
 * 调用一次,但这条不变量值得单独锁定,防止将来有人在别处不小心多调一次。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppStartTimeTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        AppStartTime.resetForTest()
    }

    @After
    fun tearDown() {
        AppStartTime.resetForTest()
    }

    @Test
    fun `record only takes effect on the first call`() {
        AppStartTime.record(context)
        val firstReference = AppStartTime.referenceUptimeMillis()
        assertNotNull(firstReference)

        AppStartTime.record(context)
        assertEquals(firstReference, AppStartTime.referenceUptimeMillis())
    }

    @Test
    fun `elapsedSinceStartMillis is null before record is called`() {
        assertEquals(null, AppStartTime.elapsedSinceStartMillis())
    }
}
