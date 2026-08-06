package io.sanato.apptemplate.core.telemetry.crash

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sanato.apptemplate.core.telemetry.DiagnosticLevel
import io.sanato.apptemplate.core.telemetry.DiagnosticLogSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 覆盖两件事:① 安装是链式的(不会丢掉之前已经设置的 handler,而是把自己
 * 套在外层、内部再转发给 previous);② handler 内的同步文件写在下次启动时
 * 能被正确读出并清空,这就是"崩溃现场不调用 Firebase SDK,下次启动才真正
 * 上报"这套设计的可验证部分。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CrashRecorderTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        // 每个测试前清空上一个测试可能留下的崩溃标记 + 默认 handler。
        CrashRecorder.drainPendingCrashReports(context)
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    @Test
    fun `install chains to the previous handler`() {
        var previousHandlerInvoked = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> previousHandlerInvoked = true }

        val recorder = CrashRecorder.install(context)
        val thread = Thread.currentThread()
        val error = IllegalStateException("boom")

        recorder.uncaughtException(thread, error)

        assertTrue(previousHandlerInvoked)
    }

    @Test
    fun `uncaught exception is drained on next launch and then cleared`() {
        CrashRecorder.install(context)
        val handler = Thread.getDefaultUncaughtExceptionHandler()!!

        handler.uncaughtException(Thread.currentThread(), IllegalStateException("first crash"))

        val firstDrain = CrashRecorder.drainPendingCrashReports(context)
        assertEquals(1, firstDrain.size)
        assertTrue(firstDrain[0].contains("first crash"))

        val secondDrain = CrashRecorder.drainPendingCrashReports(context)
        assertTrue(secondDrain.isEmpty())
    }

    @Test
    fun `sink receives exactly one FATAL and previousHandler still runs if the sink throws`() {
        var previousHandlerInvoked = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> previousHandlerInvoked = true }

        val received = mutableListOf<DiagnosticLevel>()
        val throwingSink =
            DiagnosticLogSink { level, _, _, _ ->
                received.add(level)
                throw IllegalStateException("sink misbehaving")
            }
        val recorder = CrashRecorder.install(context, throwingSink)

        recorder.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))

        assertEquals(listOf(DiagnosticLevel.FATAL), received)
        assertTrue("a throwing sink must not prevent previousHandler from running", previousHandlerInvoked)
    }

    @Test
    fun `default install with no sink behaves exactly as before`() {
        val recorder = CrashRecorder.install(context)
        recorder.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))
        assertEquals(1, CrashRecorder.drainPendingCrashReports(context).size)
    }
}
