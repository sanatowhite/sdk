package io.sanato.logkit

/**
 * 确定性测试接缝之一。刻意只用 JDK 原语(`System.currentTimeMillis`/
 * `System.nanoTime`),不用 `android.os.SystemClock`——这样纯 JUnit 测试
 * (无 Robolectric)也能注入 [FakeClock],不必为了摆动时钟去起一个 Context。
 *
 * `nanoTime()` 是本模块里超时计算(`flushBlocking`)与退避推进的唯一时间源:
 * 它单调、不受墙钟回拨影响。`wallMillis()` 只用于展示,绝不参与排序或超时。
 */
internal interface Clock {
    fun wallMillis(): Long

    fun nanoTime(): Long
}

internal object SystemClockSource : Clock {
    override fun wallMillis(): Long = System.currentTimeMillis()

    override fun nanoTime(): Long = System.nanoTime()
}
