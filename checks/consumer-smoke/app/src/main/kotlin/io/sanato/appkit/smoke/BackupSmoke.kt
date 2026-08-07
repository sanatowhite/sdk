package io.sanato.appkit.smoke

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sanato.appkit.backup.core.BackupDataSource
import io.sanato.appkit.backup.core.BackupLogger
import io.sanato.appkit.backup.core.BackupRecord
import io.sanato.appkit.backup.core.BackupRestoreTarget
import io.sanato.appkit.backup.core.PassphraseProvider
import io.sanato.appkit.backup.core.RecordSummary
import io.sanato.appkit.backup.drive.DriveBackupStore
import io.sanato.appkit.backup.drive.GmsDriveAuthorizer
import io.sanato.appkit.backup.format.Sbk1BackupCodec
import io.sanato.appkit.backup.remote.BackupOrchestrator
import io.sanato.appkit.backup.remote.RemoteBackupStore
import io.sanato.appkit.core.common.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 逐个引用 `:backupkit`/`:backupkit-drive` 的公开入口——这两个模块最容易判定错误的
 * `api`/`implementation` 边界分别是：`CoroutineDispatcher` 出现在 [BackupOrchestrator]
 * 的公开构造签名上（`:backupkit` 对 `kotlinx-coroutines-android` 用 `api` 的唯一理由）、
 * [DriveBackupStore] 实现 [RemoteBackupStore]（`:backupkit-drive` 对 `:backupkit` 用
 * `api` 而非 `implementation` 的唯一理由——消费方必须能像这里一样把它当
 * `RemoteBackupStore` 类型声明）。任何一条判定错误，这个文件就会出现 unresolved
 * reference，编译直接失败。
 *
 * 只做编译期验证：`orchestrator` 从不被调用任何 suspend 方法（`backupEntryDelta`/
 * `writeSnapshot`/`restore` 全部不触发），`GmsDriveAuthorizer` 的构造函数本身不做
 * 任何网络/GMS 调用（见其 KDoc），所以这个类的实例化本身不会在真机上崩溃或联网，
 * 和 `NetSmoke`/`DataSmoke` 的"只验证类型解析"原则一致。
 */
@Singleton
class BackupSmoke
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) {
        private val dataSource =
            object : BackupDataSource {
                override val payloadSchema: String = "io.sanato.appkit.smoke"
                override val payloadSchemaVersion: Int = 1

                override suspend fun listRecordSummaries(): List<RecordSummary> = emptyList()

                override suspend fun loadRecord(id: String): BackupRecord =
                    error("consumer-smoke has no real records — compile-time check only")

                override suspend fun resolveLocalMedia(name: String): File =
                    error("consumer-smoke has no real media — compile-time check only")
            }

        private val restoreTarget =
            object : BackupRestoreTarget {
                override fun mediaDirectory(): File = context.cacheDir

                override suspend fun accept(
                    record: BackupRecord,
                    resolvedMedia: Map<String, File>,
                ): Boolean = false
            }

        private val passphraseProvider = PassphraseProvider { ByteArray(0) }

        private val driveStore: RemoteBackupStore =
            DriveBackupStore(
                tokenProvider = GmsDriveAuthorizer(context).asTokenProvider(),
                rootFolderName = "consumer-smoke",
            )

        val orchestrator =
            BackupOrchestrator(
                store = driveStore,
                dataSource = dataSource,
                restoreTarget = restoreTarget,
                sealPassphrase = passphraseProvider,
                unsealPassphrases = emptyList(),
                workDir = context.cacheDir,
                codec = Sbk1BackupCodec(),
                logger = BackupLogger.None,
                ioDispatcher = ioDispatcher,
            )
    }
