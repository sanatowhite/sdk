package io.sanato.logkit

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.sanato.logkit.format.Envelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CyclicBarrier

/**
 * [LogKitCore] 层面的并发/持久性测试——需要真实 `Application`(供
 * [ProcessTag.resolve] 与 `filesDir`),所以用 Robolectric,`@Config(sdk = [34])`
 * 按类钉,不建 robolectric.properties,和 `:updatechecker` 的既有约定一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogKitCoreTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    private fun testConfig(builder: LogKitConfig.Builder.() -> Unit = {}): LogKitConfig {
        val kp = Envelope.generateRecipientKeyPair()
        testPrivateKey = kp.private
        return LogKitConfig
            .Builder()
            .setRecipientPublicKey(1, kp.public.encoded)
            .apply(builder)
            .build()
    }

    private lateinit var testPrivateKey: java.security.PrivateKey

    @Test
    fun `total ordering holds under concurrent writers`() {
        repeat(20) { iteration ->
            val core = LogKitCore(application, testConfig { setMaxMessageBytes(200) })
            core.start()
            val threadCount = 8
            val perThread = 200
            val barrier = CyclicBarrier(threadCount)
            val threads =
                (0 until threadCount).map { t ->
                    Thread {
                        barrier.await()
                        repeat(perThread) { i -> core.enqueue(LogLevel.INFO, "t$t", "t=$t i=$i", null) }
                    }
                }
            threads.forEach { it.start() }
            threads.forEach { it.join(10_000) }
            assertTrue("flush timed out on iteration $iteration", core.flushBlocking(10_000))

            val decoded =
                TestLogFileDecoder.decodeAllRecords(
                    FileLogDirectory(java.io.File(application.filesDir, "logkit")).list(),
                    testPrivateKey,
                )
            val seqs = decoded.map { it.seq }
            assertEquals("duplicate seq values on iteration $iteration", seqs.size, seqs.toSet().size)
            assertEquals("not strictly increasing on iteration $iteration", seqs.sorted(), seqs)

            // 每个线程自己写的记录,读回来的相对顺序必须和它自己发出的顺序一致。
            (0 until threadCount).forEach { t ->
                val ownSeqsInFileOrder =
                    decoded.filter { it.tag == "t$t" }.map {
                        it.message
                            .substringAfter(
                                "i=",
                            ).toInt()
                    }
                assertEquals(
                    "thread $t's own order was not preserved on iteration $iteration",
                    ownSeqsInFileOrder.sorted(),
                    ownSeqsInFileOrder,
                )
            }

            core.stopForTest()
            application.filesDir.deleteRecursively()
        }
    }

    @Test
    fun `overflow policy reserves capacity for WARN and ERROR`() {
        // 故意不 start() 写线程——approxQueueSize 只在 admit 时增、drain 时减,
        // 不起写线程就没有任何东西会去减它,溢出策略因此完全确定性可测。
        val capacity = 16
        val reserve = capacity / 4
        val debugCeiling = capacity - reserve // 12
        val core = LogKitCore(application, testConfig { setQueueCapacity(capacity) })

        repeat(100) { core.enqueue(LogLevel.DEBUG, "t", "d$it", null) }
        assertEquals((100 - debugCeiling).toLong(), core.droppedRecordCount())

        // DEBUG 已经把非保留区挤满,但保留带里还能再收 WARN/ERROR——这正是
        // "DEBUG 洪水挤不掉崩溃旁边的 ERROR"这条不变量。
        val droppedBeforeError = core.droppedRecordCount()
        core.enqueue(LogLevel.ERROR, "t", "important", null)
        assertEquals(droppedBeforeError, core.droppedRecordCount())

        // 保留带用满之后,再来的 ERROR 也会被丢——保留带不是无限的。
        repeat(reserve - 1) { core.enqueue(LogLevel.WARN, "t", "w$it", null) }
        val droppedBeforeOverflowingReserve = core.droppedRecordCount()
        core.enqueue(LogLevel.WARN, "t", "one too many", null)
        assertTrue(core.droppedRecordCount() > droppedBeforeOverflowingReserve)
    }

    @Test
    fun `fatal writes then synchronously flushes without deadlocking`() {
        val core = LogKitCore(application, testConfig())
        core.start()
        val ok = core.fatal("crash", "boom", RuntimeException("boom"))
        assertTrue(ok)

        val decoded =
            TestLogFileDecoder.decodeAllRecords(
                FileLogDirectory(java.io.File(application.filesDir, "logkit")).list(),
                testPrivateKey,
            )
        assertTrue(decoded.any { it.level == LogLevel.ERROR.wireValue && it.message.contains("boom") })
    }

    @Test
    fun `flushBlocking before install is vacuously true`() {
        LogKit.resetForTest()
        assertTrue(LogKit.flushBlocking(100))
        assertTrue(LogKit.fatal("t", "m", null))
        assertEquals(0L, LogKit.droppedRecordCount())
    }

    @Test
    fun `logging before install is a silent no-op`() {
        LogKit.resetForTest()
        LogKit.i("t", "should not crash")
        LogKit.e("t", "should not crash", RuntimeException())
        assertTrue(!LogKit.isHealthy())
    }

    @Test
    fun `install twice returns false the second time and does not reconfigure`() {
        LogKit.resetForTest()
        val config1 = testConfig { setMaxFileBytes(999_999) }
        assertTrue(LogKit.install(application, config1))
        val config2 = testConfig { setMaxFileBytes(1) }
        assertEquals(false, LogKit.install(application, config2))
        LogKit.resetForTest()
    }

    @Test
    fun `export produces a zip containing every sealed file plus a manifest`() {
        val core = LogKitCore(application, testConfig())
        core.start()
        repeat(50) { core.enqueue(LogLevel.INFO, "t", "record $it", null) }
        core.flushBlocking(5000)

        val dest = java.io.File(application.cacheDir, "export-test.zip")
        assertTrue(core.export(dest))
        assertTrue(dest.exists() && dest.length() > 0)

        java.util.zip.ZipFile(dest).use { zip ->
            val names =
                zip
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .toList()
            assertTrue(names.contains("manifest.txt"))
            assertTrue(names.any { it.endsWith(".logkit") })
        }
    }

    @Test
    fun `purge removes every log file`() {
        val core = LogKitCore(application, testConfig())
        core.start()
        repeat(20) { core.enqueue(LogLevel.INFO, "t", "record $it", null) }
        core.flushBlocking(5000)
        assertTrue(core.stats().files.isNotEmpty())

        core.purge()
        assertEquals(emptyList<LogFileInfo>(), core.stats().files)
    }

    @Test
    fun `public API surface is fully exercisable end to end`() {
        LogKit.resetForTest()
        val kp = Envelope.generateRecipientKeyPair()
        val config =
            LogKitConfig
                .Builder()
                .setMinLevel(LogLevel.VERBOSE)
                .setQueueCapacity(512)
                .setMaxFileBytes(64 * 1024)
                .setTotalBudgetBytes(256 * 1024)
                .setMaxFileCount(5)
                .setMirrorToLogcat(false)
                .setMaxMessageBytes(1024)
                .setFrameLingerMillis(10)
                .setMaxFrameBytes(4096)
                .setFatalFlushTimeoutMillis(1000)
                .setRecipientPublicKey(5, kp.public.encoded)
                .putMetadata("sdkVersion", "1")
                .build()

        assertTrue(LogKit.install(application, config))
        assertTrue(LogKit.isInstalled())
        LogKit.v("t", "v")
        LogKit.d("t", "d")
        LogKit.i("t", "i")
        LogKit.w("t", "w")
        LogKit.e("t", "e")
        LogKit.e("t", "e2", RuntimeException("x"))
        assertTrue(LogKit.flushBlocking(2000))
        val stats = LogKit.stats()
        assertTrue(stats.files.isNotEmpty())
        assertTrue(LogKit.export(java.io.File(application.cacheDir, "smoke.zip")))
        assertEquals(0L, LogKit.droppedRecordCount())
        LogKit.purge()
        LogKit.resetForTest()
    }
}
