package io.sanato.logkit

/**
 * [LogWriter] 和 [LogKitCore] 之间关于"flush 到哪个 seq 了"的窄接口——
 * 拆出来是为了让 [LogWriter] 的构造函数不必知道 [LogKitCore] 的全部内部状态。
 */
internal interface FlushCoordinator {
    /** 当前最大的、有人在等待被 sync 到的 seq;没有待处理的 flush 时返回 -1。 */
    fun pendingFlushTarget(): Long

    /** 写线程确认某个 seq 已经落盘并 fsync 过,唤醒所有在等的 [LogKitCore.flushBlocking]。 */
    fun onSynced(throughSeq: Long)

    /**
     * 写线程从队列里拿走了 [count] 条(不管是 `poll` 一条还是 `drainTo` 一批)。
     * `approxQueueSize` 只在入队时 `incrementAndGet`、在这里 `decrementAndGet`,
     * 从不查 `ArrayBlockingQueue.size()`——那样每条日志都要多拿一次锁,见
     * [LogKitCore] 溢出策略的文档。
     */
    fun onDrained(count: Int)
}
