package io.sanato.logkit

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 确定性测试接缝。真正的调用方永远是 [ArrayBlockingRecordQueue];测试大多用
 * [ImmediateRecordQueue],让 `offer()` 在调用方线程内联跑写路径——没有线程、
 * 没有 sleep、没有 flake,见 logkit/README.md 的测试小节。
 *
 * 选 `ArrayBlockingQueue` 而不是 `LinkedBlockingQueue`(每次 offer 多分配一个
 * Node)或手写无锁 MPSC 环(需要验证正确性的成本远超收益,见 [io.sanato.logkit]
 * 包文档),是有意的取舍,不是没想过替代方案。
 */
internal interface RecordQueue {
    fun offer(item: QueueItem): Boolean

    fun poll(timeoutMillis: Long): QueueItem?

    fun drainTo(
        out: MutableList<QueueItem>,
        max: Int,
    ): Int

    fun approxSize(): Int
}

internal class ArrayBlockingRecordQueue(
    capacity: Int,
) : RecordQueue {
    private val delegate = ArrayBlockingQueue<QueueItem>(capacity)

    override fun offer(item: QueueItem): Boolean = delegate.offer(item)

    override fun poll(timeoutMillis: Long): QueueItem? =
        if (timeoutMillis <= 0) {
            delegate.poll()
        } else {
            delegate.poll(timeoutMillis, TimeUnit.MILLISECONDS)
        }

    override fun drainTo(
        out: MutableList<QueueItem>,
        max: Int,
    ): Int = delegate.drainTo(out, max)

    override fun approxSize(): Int = delegate.size
}

/**
 * `offer()` 立即在调用方线程上跑 [onOffer]——用于让测试摆脱真实写线程/sleep。
 * 生产路径从不使用。
 */
internal class ImmediateRecordQueue(
    private val onOffer: (QueueItem) -> Unit,
) : RecordQueue {
    override fun offer(item: QueueItem): Boolean {
        onOffer(item)
        return true
    }

    override fun poll(timeoutMillis: Long): QueueItem? = null

    override fun drainTo(
        out: MutableList<QueueItem>,
        max: Int,
    ): Int = 0

    override fun approxSize(): Int = 0
}
