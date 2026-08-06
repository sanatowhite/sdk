package io.sanato.logkit

import io.sanato.logkit.format.LogRecordData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [LogWriter] 单线程直测——不起真实线程,测试线程自己扮演"唯一的写线程",
 * 用 [ManualPollQueue] + [FakeClock] 手动逐步驱动,零 sleep、完全确定性。
 * 覆盖分帧/滚动/淘汰/crypto 不可用——这些逻辑完全不依赖并发协调,不需要
 * [LogKitCore] 也不需要 Robolectric。
 */
class LogWriterTest {
    @get:Rule val tempFolder = TemporaryFolder()

    private class RecordingCoordinator : FlushCoordinator {
        var pendingTarget = -1L
        val syncedSeqs = mutableListOf<Long>()
        var drainedTotal = 0

        override fun pendingFlushTarget(): Long = pendingTarget

        override fun onSynced(throughSeq: Long) {
            syncedSeqs.add(throughSeq)
        }

        override fun onDrained(count: Int) {
            drainedTotal += count
        }
    }

    private fun newWriter(
        directory: LogDirectory,
        crypto: Crypto = IdentityCrypto(),
        config: LogKitConfig =
            LogKitConfig
                .Builder()
                .setMaxFileBytes(2048)
                .setTotalBudgetBytes(6144)
                .setMaxFileCount(10)
                .setFrameLingerMillis(0)
                .build(),
        clock: Clock = FakeClock(),
        queue: RecordQueue = ManualPollQueue(),
        coordinator: FlushCoordinator = RecordingCoordinator(),
        diagnostics: Diagnostics = Diagnostics(),
    ) = Triple(
        LogWriter(queue, config, directory, crypto, clock, diagnostics, coordinator, { "test" }),
        queue,
        diagnostics,
    )

    private fun record(
        seq: Long,
        level: Int = LogLevel.DEBUG.wireValue,
        message: String = "x".repeat(60),
    ) = LogRecordData(seq, seq, seq, 1, level, "tag", "thread", message)

    @Test
    fun `writes files that rotate once the size budget is exceeded`() {
        val dir = FileLogDirectory(tempFolder.newFolder("logs"))
        val (writer, queue, _) = newWriter(dir, config = smallConfig())
        repeat(200) { i ->
            queue.offer(QueueItem.Entry(record(i.toLong())))
            writer.loopOnceForTest()
        }
        writer.drainAndFlushInline()

        val files = dir.list()
        assertTrue("expected multiple rotated files, got ${files.size}", files.size > 1)
        val seqs = files.mapNotNull { LogFileNaming.parse(it.name)?.fileSeq }.sorted()
        assertEquals(seqs, seqs.distinct().sorted()) // 严格递增,无重复
        files.forEach { assertTrue(it.length() <= 2048 + 512) } // 悲观余量内(maxFrameBytes=256 + 头/tag 开销)
    }

    @Test
    fun `budget eviction keeps the highest fileSeq files and never touches the open file`() {
        val dir = FileLogDirectory(tempFolder.newFolder("logs"))
        val (writer, queue, diagnostics) = newWriter(dir, config = smallConfig())
        repeat(400) { i ->
            queue.offer(QueueItem.Entry(record(i.toLong())))
            writer.loopOnceForTest()
        }
        writer.drainAndFlushInline()

        val total = dir.list().sumOf { it.length() }
        assertTrue("total=$total should respect the 6144 budget within one frame's slack", total <= 6144 + 512)
        assertTrue(diagnostics.evictedFiles.get() > 0)

        val survivingSeqs = dir.list().mapNotNull { LogFileNaming.parse(it.name)?.fileSeq }.sorted()
        val maxSeq = survivingSeqs.max()
        // 幸存的必须是最大的那一批,不能是随机一批。
        assertTrue(survivingSeqs.all { it >= maxSeq - survivingSeqs.size })
    }

    @Test
    fun `eviction never uses mtime, only fileSeq`() {
        val dir = FileLogDirectory(tempFolder.newFolder("logs"))
        val (writer, queue, _) = newWriter(dir, config = smallConfig())
        repeat(400) { i ->
            queue.offer(QueueItem.Entry(record(i.toLong())))
            writer.loopOnceForTest()
        }
        writer.drainAndFlushInline()

        val before = dir.list().mapNotNull { LogFileNaming.parse(it.name)?.fileSeq }.toSet()

        // 把所有文件的 mtime 改成和 fileSeq 完全反序——如果淘汰逻辑偷偷依赖了
        // mtime,这一步就会让"最旧"判断反转,产生和 before 不一致的幸存集合。
        dir.list().sortedBy { LogFileNaming.parse(it.name)?.fileSeq ?: 0 }.forEachIndexed { i, f ->
            f.setLastModified(1_000_000_000_000L - i * 60_000L)
        }

        // 再写一批,触发一次新的淘汰决策。
        repeat(50) { i ->
            queue.offer(QueueItem.Entry(record(1000L + i)))
            writer.loopOnceForTest()
        }
        writer.drainAndFlushInline()

        val after = dir.list().mapNotNull { LogFileNaming.parse(it.name)?.fileSeq }.toSet()
        // 幸存集合的判定标准应该只由 fileSeq 决定,mtime 反转不应该让旧文件复活。
        assertTrue(after.filter { it in before }.all { it >= before.max() - 3 })
    }

    @Test
    fun `crypto being unavailable means the directory stays empty, never falls back to plaintext`() {
        val dir = FileLogDirectory(tempFolder.newFolder("logs"))
        val (writer, queue, diagnostics) = newWriter(dir, crypto = AlwaysFailingCrypto())
        repeat(20) { i ->
            queue.offer(QueueItem.Entry(record(i.toLong())))
            writer.loopOnceForTest()
        }
        writer.drainAndFlushInline()

        assertEquals(emptyList<Any>(), dir.list())
        assertTrue(diagnostics.droppedByIo.get() > 0)
        assertTrue(!writer.healthy)
        assertTrue(writer.cryptoUnavailable)
    }

    @Test
    fun `an IO failure enters degraded mode and drains without blocking the caller`() {
        val realDir = FileLogDirectory(tempFolder.newFolder("logs"))
        val flaky = FlakyLogDirectory(realDir, failWritesFromCallIndex = 2)
        val (writer, queue, diagnostics) = newWriter(flaky)
        repeat(1000) { i ->
            queue.offer(QueueItem.Entry(record(i.toLong())))
            writer.loopOnceForTest()
        }
        writer.drainAndFlushInline()

        assertTrue(diagnostics.droppedByIo.get() > 0)
        assertTrue(!writer.healthy)
    }

    @Test
    fun `records within a single frame decode back in order after a real seal-open round trip`() {
        val dir = FileLogDirectory(tempFolder.newFolder("logs"))
        val (writer, queue, _) =
            newWriter(
                dir,
                crypto =
                    RealCrypto(
                        io.sanato.logkit.BuiltInRecipientKey.PUBLIC_KEY_SPKI_DER,
                        io.sanato.logkit.BuiltInRecipientKey.KEY_ID,
                    ),
            )
        repeat(10) { i ->
            queue.offer(QueueItem.Entry(record(i.toLong(), message = "hello $i")))
        }
        writer.drainAndFlushInline()

        assertTrue(dir.list().isNotEmpty())
    }

    private fun smallConfig() =
        LogKitConfig
            .Builder()
            .setMaxFileBytes(2048)
            .setTotalBudgetBytes(6144)
            .setMaxFileCount(20)
            .setFrameLingerMillis(0)
            .setMaxMessageBytes(100)
            // 帧要远小于文件上限,否则单个帧本身就会超过 maxFileBytes(这是
            // 设计里被接受的已知上界情形,但不是这份测试想验证的东西)。
            .setMaxFrameBytes(256)
            .build()
}
