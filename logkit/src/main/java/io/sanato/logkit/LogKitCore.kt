package io.sanato.logkit

import android.app.Application
import io.sanato.logkit.format.LogRecordData
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 单个已安装实例的全部跨线程协调都收在这里——[LogWriter] 本身单线程构造、
 * 零内部同步,这个类是它与外界(调用方线程、`flushBlocking` 等待者)之间
 * 唯一的接触面。
 */
internal class LogKitCore(
    application: Application,
    private val config: LogKitConfig,
    queueFactory: (RecordQueue) -> RecordQueue = { it },
    private val directory: LogDirectory =
        FileLogDirectory(File(application.filesDir, "logkit")),
    crypto: Crypto = RealCrypto(config.recipientPublicKeyDer, config.recipientKeyId),
    private val clock: Clock = SystemClockSource,
) : FlushCoordinator {
    private val diagnostics = Diagnostics()
    private val seq = AtomicLong(0)
    private val approxQueueSize = AtomicInteger(0)
    private val flushRequestedTo = AtomicLong(-1)
    private val syncedThroughSeq = AtomicLong(-1)

    // 用 java.lang.Object 而不是 kotlin.Any——wait()/notifyAll() 是 Object 的方法,
    // Kotlin 故意没在 Any 上暴露它们(建议用 java.util.concurrent 原语),但这里
    // 恰恰就是要用最原始的 monitor wait/notify 语义,没有更简单的替代。
    private val flushMonitor = Object()

    private val queue: RecordQueue = queueFactory(ArrayBlockingRecordQueue(config.queueCapacity))
    private val writer: LogWriter =
        LogWriter(
            queue = queue,
            config = config,
            directory = directory,
            crypto = crypto,
            clock = clock,
            diagnostics = diagnostics,
            coordinator = this,
            resolveProcessTag = { ProcessTag.resolve(application) },
        )
    private val writerThread: Thread =
        Thread(writer, "logkit-writer").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY + 1
        }

    fun start() {
        writerThread.start()
    }

    /** 测试专用清理——公开 API 刻意没有 `shutdown()`(见 [LogKit] 的文档),
     *  这里只是让测试之间不要无限堆积后台线程,不代表生产语义的一部分。 */
    internal fun stopForTest() {
        writer.stop()
    }

    fun enqueue(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        if (level.wireValue < config.minLevel.wireValue) return
        val finalMessage =
            if (throwable != null) {
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                "$message\n$sw"
            } else {
                message
            }
        val thread = Thread.currentThread()

        // Thread.getId() 在新版 JDK 里被 threadId() 取代,但后者是 JDK 19+ API,
        // minSdk 24 的真机与 Robolectric 都不保证有——getId() 在所有 Android
        // API level 上行为不变,这里刻意继续用它,只压掉桌面 JDK 的弃用告警。
        @Suppress("DEPRECATION")
        val threadIdValue = thread.id.toInt()
        val record =
            LogRecordData(
                seq = 0, // 下面按录取结果赋值
                wallMillis = clock.wallMillis(),
                elapsedNanos = clock.nanoTime(),
                threadId = threadIdValue,
                level = level.wireValue,
                tag = tag,
                threadName = thread.name,
                message = finalMessage,
            )
        admit(record)
    }

    /**
     * "打序号"和"入队"必须在同一把锁里做成一个原子操作——两个并发调用方各自
     * `getAndIncrement()` 拿到 5 和 6 之后,谁先真正 `offer()` 进队列完全取决于
     * 线程调度;分成两步就意味着 seq 只保证【唯一】,不保证【入队顺序 == seq
     * 顺序】,而后者才是"并发时保证顺序一致性"这个需求真正要的东西。临界区
     * 极短(一次自增 + 一次 `ArrayBlockingQueue.offer()`,没有 IO),这把锁的
     * 开销和 `ArrayBlockingQueue` 内部本来就有的那把锁是同一量级。
     *
     * 两档保留带:ERROR/WARN 用满整条队列;更低级别只用到 `capacity - capacity/4`,
     * 保证 DEBUG 洪水挤不掉崩溃旁边的 ERROR。用自己的 [approxQueueSize] 判断,
     * 不查 `ArrayBlockingQueue.size()`——那样每条日志都要多拿一次锁。
     */
    private val admitLock = Any()

    private fun admit(template: LogRecordData) {
        synchronized(admitLock) {
            val reserve = config.queueCapacity / 4
            val ceiling =
                if (template.level >=
                    LogLevel.WARN.wireValue
                ) {
                    config.queueCapacity
                } else {
                    config.queueCapacity - reserve
                }
            if (approxQueueSize.get() >= ceiling) {
                diagnostics.recordOverflowDrop(seq.get())
                return
            }
            val assignedSeq = seq.getAndIncrement()
            val record = template.copy(seq = assignedSeq)
            if (queue.offer(QueueItem.Entry(record))) {
                approxQueueSize.incrementAndGet()
            } else {
                diagnostics.recordOverflowDrop(assignedSeq)
            }
        }
    }

    fun flushBlocking(timeoutMillis: Long): Boolean {
        if (Thread.currentThread() === writerThread) {
            return try {
                writer.drainAndFlushInline()
                true
            } catch (t: Throwable) {
                false
            }
        }
        val target = seq.get() - 1 // 已经【接受】的最大 seq;还没被接受的不算数。
        if (target <= syncedThroughSeq.get()) return true
        flushRequestedTo.getAndUpdate { current -> maxOf(current, target) }
        queue.offer(QueueItem.FlushBarrier) // 队列满时静默失败也无妨——写线程会自己追上,见 LogWriter。
        val deadlineNanos = clock.nanoTime() + timeoutMillis * 1_000_000
        synchronized(flushMonitor) {
            while (syncedThroughSeq.get() < target) {
                val remainingNanos = deadlineNanos - clock.nanoTime()
                if (remainingNanos <= 0) return syncedThroughSeq.get() >= target
                val remainingMillis = remainingNanos / 1_000_000
                flushMonitor.wait(maxOf(remainingMillis, 1))
            }
        }
        return true
    }

    fun fatal(
        tag: String,
        message: String,
        throwable: Throwable?,
    ): Boolean {
        enqueue(LogLevel.ERROR, tag, message, throwable)
        return flushBlocking(config.fatalFlushTimeoutMillis)
    }

    fun isHealthy(): Boolean = writer.healthy

    fun droppedRecordCount(): Long = diagnostics.droppedByOverflow.get() + diagnostics.droppedByIo.get()

    fun purge(): Int {
        queue.offer(QueueItem.RotateBarrier)
        flushBlocking(config.fatalFlushTimeoutMillis)
        return LogExporter.purgeAll(directory)
    }

    fun export(destination: File): Boolean {
        // 先封盘再打包:导出的 zip 里绝不能含一个仍在增长的文件。
        queue.offer(QueueItem.RotateBarrier)
        flushBlocking(config.fatalFlushTimeoutMillis)
        return LogExporter.export(directory, destination)
    }

    fun stats(): LogKitStats = statsInternal()

    private fun statsInternal(): LogKitStats {
        val files = directory.list().sortedBy { LogFileNaming.parse(it.name)?.fileSeq ?: -1L }
        val total = files.sumOf { it.length() }
        return buildStats(
            files = files,
            lengthOf = { it.length() },
            totalBytes = total,
            budgetBytes = config.totalBudgetBytes,
            queuedRecords = approxQueueSize.get(),
            nextSequence = seq.get(),
            droppedRecords = droppedRecordCount(),
            evictedFiles = diagnostics.evictedFiles.get(),
            persistenceHealthy = isHealthy(),
            keyId = config.recipientKeyId,
        )
    }

    // FlushCoordinator——写线程通过这两个方法和调用方线程对话,见 LogWriter 的文档。
    override fun pendingFlushTarget(): Long = flushRequestedTo.get()

    override fun onSynced(throughSeq: Long) {
        syncedThroughSeq.getAndUpdate { current -> maxOf(current, throughSeq) }
        synchronized(flushMonitor) { flushMonitor.notifyAll() }
    }

    override fun onDrained(count: Int) {
        approxQueueSize.getAndUpdate { current -> maxOf(0, current - count) }
    }
}
