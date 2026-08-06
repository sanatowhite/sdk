package io.sanato.logkit

import java.util.concurrent.atomic.AtomicLong

/** 全部计数器,跨线程读写,无锁。 */
internal class Diagnostics {
    val droppedByOverflow = AtomicLong(0)
    val droppedByIo = AtomicLong(0)
    val frameWriteFailures = AtomicLong(0)
    val evictedFiles = AtomicLong(0)
    val firstDroppedSeq = AtomicLong(-1)

    fun recordOverflowDrop(seq: Long) {
        droppedByOverflow.incrementAndGet()
        firstDroppedSeq.compareAndSet(-1, seq)
    }
}
