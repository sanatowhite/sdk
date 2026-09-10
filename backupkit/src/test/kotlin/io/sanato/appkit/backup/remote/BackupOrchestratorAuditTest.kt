package io.sanato.appkit.backup.remote

import io.sanato.appkit.backup.core.BackupRecord
import io.sanato.appkit.backup.core.PassphraseProvider
import io.sanato.appkit.backup.testing.FakeBackupDataSource
import io.sanato.appkit.backup.testing.FakeBackupRestoreTarget
import io.sanato.appkit.backup.testing.InMemoryRemoteBackupStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [BackupOrchestrator.auditRemote] —— 只读对账。
 *
 * 存在的理由：消费方（sanato-diary）此前**无法回答"我的数据真的都备份上去了吗"**。
 * `writeSnapshot` 的回读校验（不变式②）只断言"文件存在且非空"，一份解不开的密文同样能通过；
 * 而 `restore()` 能证明可恢复性，代价是真往宿主库里写数据，不能拿来当自检用。
 *
 * auditRemote 填的就是这个空档：下载最新快照、解密、只读 manifest（[ArchiveReader.peekManifest]，
 * 不往媒体目录落盘）、再列一遍 entries/ 与 media/，报告云端**实际**覆盖了哪些 record。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupOrchestratorAuditTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val passphrase = PassphraseProvider { "test-passphrase".toByteArray() }

    private fun orchestrator(
        store: InMemoryRemoteBackupStore,
        dataSource: FakeBackupDataSource,
    ) = BackupOrchestrator(
        store = store,
        dataSource = dataSource,
        restoreTarget = FakeBackupRestoreTarget(tmp.newFolder()),
        sealPassphrase = passphrase,
        unsealPassphrases = listOf(passphrase),
        workDir = tmp.newFolder(),
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private fun record(
        id: String,
        media: List<String> = emptyList(),
    ) = BackupRecord(
        id = id,
        createdAtMillis = 1_000L + id.hashCode().toLong().and(0xff),
        modifiedAtMillis = 2_000L,
        body = """{"id":"$id"}""",
        mediaNames = media,
    )

    @Test
    fun reportsEveryRecordIdThatTheLatestSnapshotActuallyContains() =
        runTest {
            val store = InMemoryRemoteBackupStore()
            val dataSource = FakeBackupDataSource()
            listOf("1", "2", "3").forEach { dataSource.seed(record(it)) }
            val orch = orchestrator(store, dataSource)
            orch.writeSnapshot(snapshotTimeMillis = 5_000L)

            val audit = orch.auditRemote()

            assertNotNull(audit.snapshotName)
            assertEquals(setOf("1", "2", "3"), audit.snapshotRecordIds)
            assertEquals(setOf("1", "2", "3"), audit.coveredRecordIds)
        }

    /** 这是 auditRemote 的核心价值：光看文件列表证明不了快照能解开，解析 manifest 能。 */
    @Test
    fun provesTheSnapshotCanActuallyBeDecryptedAndParsed() =
        runTest {
            val store = InMemoryRemoteBackupStore()
            val dataSource = FakeBackupDataSource()
            dataSource.seed(record("42"))
            val orch = orchestrator(store, dataSource)
            orch.writeSnapshot(snapshotTimeMillis = 7_000L)

            val audit = orch.auditRemote()

            // manifest 的 createdAt 只能来自成功解密+解析，凭文件名/大小拿不到。
            assertEquals(7_000L, audit.snapshotCreatedAtMillis)
            assertEquals(setOf("42"), audit.snapshotRecordIds)
        }

    /** 快照之后新写的日记只在 entries/ 里——对账必须把它算成"已覆盖"，否则会谎报缺失。 */
    @Test
    fun countsDeltaEntriesAsCovered_notJustTheSnapshot() =
        runTest {
            val store = InMemoryRemoteBackupStore()
            val dataSource = FakeBackupDataSource()
            listOf("1", "2").forEach { dataSource.seed(record(it)) }
            val orch = orchestrator(store, dataSource)
            orch.writeSnapshot(snapshotTimeMillis = 5_000L)
            // 快照之后新增一篇，只走单篇增量。
            dataSource.seed(record("99"))
            orch.backupEntryDelta("99")

            val audit = orch.auditRemote()

            assertEquals(setOf("1", "2"), audit.snapshotRecordIds)
            assertEquals(setOf("99"), audit.entryRecordIds)
            assertEquals(setOf("1", "2", "99"), audit.coveredRecordIds)
        }

    /** 云端只有增量、一份快照都没有（新账号只点过保存）——不能抛，要如实报 null。 */
    @Test
    fun noSnapshotYet_reportsNullWithoutThrowing() =
        runTest {
            val store = InMemoryRemoteBackupStore()
            val dataSource = FakeBackupDataSource()
            dataSource.seed(record("7"))
            val orch = orchestrator(store, dataSource)
            orch.backupEntryDelta("7")

            val audit = orch.auditRemote()

            assertNull(audit.snapshotName)
            assertEquals(0L, audit.snapshotCreatedAtMillis)
            assertTrue(audit.snapshotRecordIds.isEmpty())
            assertEquals(setOf("7"), audit.entryRecordIds)
        }

    @Test
    fun completelyEmptyRemote_reportsEmptyAudit() =
        runTest {
            val audit = orchestrator(InMemoryRemoteBackupStore(), FakeBackupDataSource()).auditRemote()

            assertNull(audit.snapshotName)
            assertTrue(audit.coveredRecordIds.isEmpty())
            assertTrue(audit.mediaNames.isEmpty())
        }

    @Test
    fun reportsRemoteMediaLibraryNamesWithoutSealedSuffix() =
        runTest {
            val store = InMemoryRemoteBackupStore()
            val dataSource = FakeBackupDataSource()
            val photo = tmp.newFile("photo.jpg").apply { writeBytes(ByteArray(16) { 7 }) }
            dataSource.seedMedia("photo.jpg", photo)
            dataSource.seed(record("1", media = listOf("photo.jpg")))
            val orch = orchestrator(store, dataSource)
            orch.writeSnapshot(snapshotTimeMillis = 5_000L)

            val audit = orch.auditRemote()

            // 远端存的是 photo.jpg.sdb；对账要报回宿主认得的原名，否则宿主求差集会全部误判成缺失。
            assertEquals(setOf("photo.jpg"), audit.mediaNames)
        }

    /** 只读:绝不碰宿主数据。 */
    @Test
    fun auditNeverWritesToTheRestoreTarget() =
        runTest {
            val store = InMemoryRemoteBackupStore()
            val dataSource = FakeBackupDataSource()
            dataSource.seed(record("1"))
            val target = FakeBackupRestoreTarget(tmp.newFolder())
            val orch =
                BackupOrchestrator(
                    store = store,
                    dataSource = dataSource,
                    restoreTarget = target,
                    sealPassphrase = passphrase,
                    unsealPassphrases = listOf(passphrase),
                    workDir = tmp.newFolder(),
                    ioDispatcher = UnconfinedTestDispatcher(),
                )
            orch.writeSnapshot(snapshotTimeMillis = 5_000L)

            orch.auditRemote()

            assertTrue("auditRemote 不该写入宿主库", target.accepted.isEmpty())
            assertNull("auditRemote 不该触发 onRestoreStart", target.lastHeader)
        }

    /** 对账也不该改远端（不删任何东西）。 */
    @Test
    fun auditNeverMutatesTheRemote() =
        runTest {
            val store = InMemoryRemoteBackupStore()
            val dataSource = FakeBackupDataSource()
            dataSource.seed(record("1"))
            val orch = orchestrator(store, dataSource)
            orch.writeSnapshot(snapshotTimeMillis = 5_000L)
            val before = store.list(RemoteBackupStore.FOLDER_SNAPSHOTS).map { it.name }
            store.deletedIds.clear()

            orch.auditRemote()

            assertTrue("auditRemote 删了远端文件", store.deletedIds.isEmpty())
            assertEquals(before, store.list(RemoteBackupStore.FOLDER_SNAPSHOTS).map { it.name })
        }
}
