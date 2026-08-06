package io.sanato.logkit

import android.util.Log
import io.sanato.logkit.format.Envelope
import io.sanato.logkit.format.FileHeader
import io.sanato.logkit.format.FileHeaderCodec
import io.sanato.logkit.format.LogRecordData
import io.sanato.logkit.format.RecordCodec
import java.io.File
import java.io.IOException

/**
 * 写线程循环体。整条设计原则是"单线程构造 ⇒ 内部零同步"——所有可变状态
 * (当前文件、待写批次、退避计时)只被这一个线程读写,跨线程交互全部收窄到
 * [FlushCoordinator] 这一个接口。
 *
 * 调用方(调用方线程,包括 [LogKitCore.enqueue] 与崩溃处理器的 `fatal`)只通过
 * [RecordQueue] 和 [FlushCoordinator] 与这里对话,从不直接触碰下面任何字段。
 */
internal class LogWriter(
    private val queue: RecordQueue,
    private val config: LogKitConfig,
    private val directory: LogDirectory,
    private val crypto: Crypto,
    private val clock: Clock,
    private val diagnostics: Diagnostics,
    private val coordinator: FlushCoordinator,
    private val resolveProcessTag: () -> String,
    private val providePid: () -> Int = { android.os.Process.myPid() },
) : Runnable {
    @Volatile var healthy: Boolean = true
        private set

    @Volatile var directoryUnavailable: Boolean = false
        private set

    @Volatile var cryptoUnavailable: Boolean = false
        private set

    @Volatile private var running = true

    private lateinit var fileSet: LogFileSet
    private lateinit var processTag: String
    private var initialized = false

    private var currentSink: FileSink? = null
    private var currentFileBytes = 0L
    private var nextFrameIndex = 0L

    private val pendingBatch = ArrayList<LogRecordData>()
    private var pendingBytes = 0
    private var batchDeadlineNanos = -1L

    private var maxWrittenSeq = -1L
    private var lastAckedSeq = -1L

    private var ioFailureStreak = 0
    private var ioBackoffUntilNanos = 0L
    private var loggedCryptoFailureOnce = false
    private var loggedPermanentIoFailureOnce = false

    fun stop() {
        running = false
    }

    override fun run() {
        while (running) {
            try {
                loopOnce()
            } catch (t: Throwable) {
                // 写线程绝不能死——死掉会让 SDK 静默变成 /dev/null,是最坏的失败。
                Log.e(TAG, "logkit writer loop iteration failed, continuing", t)
            }
        }
    }

    /** [LogKitCore.flushBlocking] 在"调用方线程就是写线程"时走这条路——非阻塞,内联执行。 */
    fun drainAndFlushInline() {
        ensureInitialized()
        val drained = ArrayList<QueueItem>()
        queue.drainTo(drained, Int.MAX_VALUE)
        if (drained.isNotEmpty()) coordinator.onDrained(drained.size)
        drained.forEach(::handleItem)
        flushPendingBatch(flushInduced = true)
    }

    /**
     * 测试专用入口——单线程、同步跑一步循环体,不用真的起线程也不需要真实时间
     * 流逝。生产路径只通过 [run] 使用(后台线程持续调用)。
     */
    internal fun loopOnceForTest() {
        loopOnce()
    }

    private fun ensureInitialized() {
        if (!initialized) {
            onFirstWorkItem()
            initialized = true
        }
    }

    private fun onFirstWorkItem() {
        processTag = resolveProcessTag()
        fileSet =
            LogFileSet(directory, config.maxFileBytes, config.totalBudgetBytes, config.maxFileCount, diagnostics)
        try {
            fileSet.initialize()
        } catch (e: Exception) {
            directoryUnavailable = true
            healthy = false
            Log.e(TAG, "logkit could not initialize log directory, logging disabled for this process", e)
        }
    }

    private fun loopOnce() {
        ensureInitialized()
        val timeoutMillis =
            if (pendingBatch.isEmpty()) {
                Long.MAX_VALUE / 2 // 没有待写批次时无限等待下一条(用大值代替真正的“无限”,避免溢出)
            } else {
                maxOf(1L, (batchDeadlineNanos - clock.nanoTime()) / 1_000_000)
            }
        val item = queue.poll(minOf(timeoutMillis, config.frameLingerMillis.coerceAtLeast(1)))
        if (item == null) {
            if (pendingBatch.isNotEmpty() && clock.nanoTime() >= batchDeadlineNanos) {
                flushPendingBatch(flushInduced = false)
            }
        } else {
            coordinator.onDrained(1)
            handleItem(item)
        }
        maybeCatchUpOnPendingFlush()
    }

    private fun handleItem(item: QueueItem) {
        when (item) {
            is QueueItem.Entry -> {
                if (pendingBatch.isEmpty()) {
                    batchDeadlineNanos =
                        clock.nanoTime() + config.frameLingerMillis * 1_000_000L
                }
                pendingBatch.add(item.record)
                pendingBytes += estimateRecordBytes(item.record)
                if (pendingBytes >= config.maxFrameBytes) {
                    flushPendingBatch(flushInduced = false)
                }
            }

            QueueItem.FlushBarrier -> {
                flushPendingBatch(flushInduced = true)
            }

            QueueItem.RotateBarrier -> {
                flushPendingBatch(flushInduced = true)
                closeCurrentFile()
            }
        }
    }

    /** 覆盖"barrier 入队失败(队列满)"的兜底路径——写线程即使没看到 barrier,也会在追上进度后自己 sync。 */
    private fun maybeCatchUpOnPendingFlush() {
        val target = coordinator.pendingFlushTarget()
        if (target > lastAckedSeq && maxWrittenSeq >= target) {
            ackFlush()
        }
    }

    private fun ackFlush() {
        val synced = currentSink?.sync() ?: true
        if (synced) {
            lastAckedSeq = maxWrittenSeq
            coordinator.onSynced(maxWrittenSeq)
        }
        // sync 失败:不确认,让调用方在超时后拿到 false——"可能丢了记录",不是"文件坏了"。
    }

    private fun flushPendingBatch(flushInduced: Boolean) {
        if (pendingBatch.isNotEmpty()) {
            writeBatchAsFrame()
        }
        if (flushInduced) {
            ackFlush()
        }
    }

    private fun writeBatchAsFrame() {
        if (clock.nanoTime() < ioBackoffUntilNanos || directoryUnavailable || cryptoUnavailable) {
            dropPendingBatch(diagnostics.droppedByIo)
            return
        }
        // 用真实编码出来的字节数定缓冲区大小,不用 pendingBytes——那只是个
        // 按 UTF-16 char 数估算的悲观上界(用来判断"够不够触发分帧"),对多字节
        // 字符(CJK/emoji)会低估真实 UTF-8 字节数,拿它来分配目标数组会越界。
        val encodedRecords = pendingBatch.map { RecordCodec.encode(it, config.maxMessageBytes) }
        val actualPlaintext = ByteArray(encodedRecords.sumOf { it.size })
        var offset = 0
        for (encoded in encodedRecords) {
            System.arraycopy(encoded, 0, actualPlaintext, offset, encoded.size)
            offset += encoded.size
        }

        // 滚动判断必须在加密之前做,用悲观估计(明文长度 + 头 + tag + 余量)——
        // 一旦拿 sealed.size 才决定滚动,就会把已经用【旧】文件密钥/nonceSalt
        // 封好的帧写进【新】文件,密钥和 nonce 都对不上,那份帧直接不可解密。
        // 见 FrameCodec 的 nonce = nonceSalt(文件级) ‖ frameIndex —— 换文件必须
        // 先换密钥材料,再封帧,顺序不能反。
        val estimatedFrameBytes = actualPlaintext.size + FRAME_SIZE_SLACK
        if (currentSink != null && fileSet.shouldRotate(currentFileBytes, estimatedFrameBytes.toLong())) {
            closeCurrentFile()
        }

        if (!ensureFileOpen()) {
            dropPendingBatch(diagnostics.droppedByIo)
            return
        }

        val keys = currentFileKeys!!
        val sealed =
            try {
                crypto.sealFrame(
                    keys,
                    frameIndex = nextFrameIndex,
                    firstRecordSeq = pendingBatch.first().seq,
                    recordCount = pendingBatch.size,
                    plaintext = actualPlaintext,
                    compress = true,
                    flushInduced = false,
                )
            } catch (e: Exception) {
                diagnostics.frameWriteFailures.incrementAndGet()
                dropPendingBatch(diagnostics.droppedByIo)
                return
            }

        try {
            currentSink!!.write(sealed, 0, sealed.size)
            fileSet.recordWritten(currentSink!!.file, sealed.size.toLong())
            currentFileBytes += sealed.size
            nextFrameIndex++
            maxWrittenSeq = pendingBatch.last().seq
            ioFailureStreak = 0
            // 一次成功写入意味着已经从退避里恢复——只要不是永久性的
            // cryptoUnavailable/directoryUnavailable,健康状态就该反映过来。
            if (!cryptoUnavailable && !directoryUnavailable) healthy = true
            if (config.mirrorToLogcat) pendingBatch.forEach(LogcatMirror::mirror)
        } catch (e: IOException) {
            enterIoDegraded(e)
            dropPendingBatch(diagnostics.droppedByIo)
            return
        }

        pendingBatch.clear()
        pendingBytes = 0
        maybeEvict()
    }

    private var currentFileKeys: FileKeys? = null

    private fun ensureFileOpen(): Boolean {
        if (currentSink != null) return true
        fileSet.evict(openFile = null)
        val fileSeq = fileSet.nextFileSeq()
        val keys = crypto.newFileKeys(fileSeq)
        if (keys == null) {
            cryptoUnavailable = true
            healthy = false
            if (!loggedCryptoFailureOnce) {
                loggedCryptoFailureOnce = true
                Log.e(TAG, "logkit crypto self-probe failed on this device, logging permanently disabled")
            }
            return false
        }
        val wallMillis = clock.wallMillis()
        val name = LogFileNaming.buildName(fileSeq, wallMillis, processTag)
        var sink: FileSink? = null
        var attempt = 0
        while (sink == null && attempt < MAX_CREATE_ATTEMPTS) {
            sink = directory.create(name)
            attempt++
        }
        if (sink == null) {
            enterIoDegraded(IOException("could not create $name after $MAX_CREATE_ATTEMPTS attempts"))
            return false
        }
        val header =
            FileHeader(
                formatVersion = FileHeaderCodec.FORMAT_VERSION,
                kemId = Envelope.KEM_ID_ECIES_P256.toInt(),
                aeadId = 1,
                compressionId = 1,
                keyId = keys.keyId,
                nonceSalt = keys.nonceSalt,
                createdAtWallMillis = wallMillis,
                createdAtElapsedNanos = clock.nanoTime(),
                fileSeq = fileSeq,
                pid = providePid(),
                processTag = processTag,
                wrappedKey = keys.wrappedKey,
                metadata = config.metadata,
            )
        val headerBytes = FileHeaderCodec.encode(header)
        return try {
            sink.write(headerBytes, 0, headerBytes.size)
            currentSink = sink
            currentFileKeys = keys
            currentFileBytes = headerBytes.size.toLong()
            nextFrameIndex = 0
            fileSet.recordCreated(sink.file, fileSeq, processTag)
            fileSet.recordWritten(sink.file, headerBytes.size.toLong())
            true
        } catch (e: IOException) {
            sink.close()
            enterIoDegraded(e)
            false
        }
    }

    private fun closeCurrentFile() {
        currentSink?.close()
        currentSink = null
        currentFileKeys = null
        currentFileBytes = 0
        nextFrameIndex = 0
    }

    private fun maybeEvict() {
        framesSinceEvictCheck++
        bytesSinceEvictCheck += currentFileBytes
        if (framesSinceEvictCheck >= EVICT_CHECK_FRAME_INTERVAL || bytesSinceEvictCheck >= EVICT_CHECK_BYTE_INTERVAL) {
            framesSinceEvictCheck = 0
            bytesSinceEvictCheck = 0
            fileSet.evict(openFile = currentSink?.file)
        }
    }

    private var framesSinceEvictCheck = 0
    private var bytesSinceEvictCheck = 0L

    private fun dropPendingBatch(counter: java.util.concurrent.atomic.AtomicLong) {
        counter.addAndGet(pendingBatch.size.toLong())
        pendingBatch.clear()
        pendingBytes = 0
    }

    private fun enterIoDegraded(cause: IOException) {
        ioFailureStreak++
        val backoffMillis = minOf(MAX_BACKOFF_MILLIS, MIN_BACKOFF_MILLIS shl minOf(ioFailureStreak, 8))
        ioBackoffUntilNanos = clock.nanoTime() + backoffMillis * 1_000_000L
        healthy = false
        if (!loggedPermanentIoFailureOnce) {
            loggedPermanentIoFailureOnce = true
            Log.e(TAG, "logkit hit an IO failure, entering degraded/backoff mode", cause)
        }
        closeCurrentFile()
    }

    /**
     * 悲观上界,不是精确值——按 UTF-16 char 数而不是 UTF-8 字节数估计变长字段,
     * 多字节字符会让这个数比 [RecordCodec.encode] 的真实输出更大,不会更小
     * (`pendingBytes` 只用来判断"够不够触发一次分帧",偏大只会让分帧更频繁,
     * 绝不会让分配的缓冲区不够用)。40 = 4 字节 `recordLen` 前缀 + [RecordCodec]
     * 的 36 字节固定部分。
     */
    private fun estimateRecordBytes(record: LogRecordData): Int =
        40 + record.tag.length + record.threadName.length + record.message.length

    private companion object {
        const val TAG = "LogKit"
        const val MAX_CREATE_ATTEMPTS = 8
        const val MIN_BACKOFF_MILLIS = 5_000L
        const val MAX_BACKOFF_MILLIS = 5 * 60_000L
        const val EVICT_CHECK_FRAME_INTERVAL = 64
        const val EVICT_CHECK_BYTE_INTERVAL = 256 * 1024L

        /** 帧头(40B) + GCM tag(16B) + deflate 对不可压缩/极小输入的最坏膨胀余量。 */
        const val FRAME_SIZE_SLACK = 40 + 16 + 32
    }
}
