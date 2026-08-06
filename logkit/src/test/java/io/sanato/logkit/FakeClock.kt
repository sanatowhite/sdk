package io.sanato.logkit

/** 手动推进的时钟——测试用来避免任何 sleep,精确控制 linger/退避窗口。 */
internal class FakeClock(
    private var wall: Long = 1_700_000_000_000L,
    private var nanos: Long = 0L,
) : Clock {
    override fun wallMillis(): Long = wall

    override fun nanoTime(): Long = nanos

    fun advanceNanos(delta: Long) {
        nanos += delta
    }

    fun advanceMillis(delta: Long) {
        wall += delta
        nanos += delta * 1_000_000
    }

    fun setWallMillis(value: Long) {
        wall = value
    }
}
