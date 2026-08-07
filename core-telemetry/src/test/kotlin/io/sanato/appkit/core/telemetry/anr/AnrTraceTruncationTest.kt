package io.sanato.appkit.core.telemetry.anr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * 只测"读取 + 截断 + 附加提示"这段纯逻辑,不碰 `ApplicationExitInfo`——
 * Robolectric 没有现成的 shadow 能构造带自定义 `traceInputStream` 的实例,
 * 见 [AnrExitInfoReaper.readTruncated] 的文档。
 */
class AnrTraceTruncationTest {
    @Test
    fun `returns the trace unchanged when it fits within maxBytes`() {
        val text = "short stack trace"
        val result = AnrExitInfoReaper.readTruncated(ByteArrayInputStream(text.toByteArray()), maxBytes = 1024)
        assertEquals(text, result)
    }

    @Test
    fun `truncates at exactly maxBytes and appends a marker`() {
        val text = "x".repeat(1000)
        val result = AnrExitInfoReaper.readTruncated(ByteArrayInputStream(text.toByteArray()), maxBytes = 100)
        assertTrue(result.startsWith("x".repeat(100)))
        assertTrue(result.contains("truncated at 100 bytes"))
    }

    @Test
    fun `exactly maxBytes with nothing left over is not marked truncated`() {
        val text = "x".repeat(100)
        val result = AnrExitInfoReaper.readTruncated(ByteArrayInputStream(text.toByteArray()), maxBytes = 100)
        assertEquals(text, result)
    }

    @Test
    fun `handles an empty stream`() {
        val result = AnrExitInfoReaper.readTruncated(ByteArrayInputStream(ByteArray(0)), maxBytes = 100)
        assertEquals("", result)
    }
}
