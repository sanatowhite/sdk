package io.sanato.logkit

import io.sanato.logkit.format.LogRecordData

/**
 * 队列里流转的三种东西:普通记录、flush 屏障、rotate 屏障。屏障没有 payload,
 * 用 object 而不是额外的旁路信号(见 [io.sanato.logkit.LogKitCore.flushBlocking]
 * 对"哨兵入队失败时靠 `flushRequestedTo` 兜底"的说明)。
 */
internal sealed class QueueItem {
    internal class Entry(
        val record: LogRecordData,
    ) : QueueItem()

    internal object FlushBarrier : QueueItem()

    internal object RotateBarrier : QueueItem()
}
