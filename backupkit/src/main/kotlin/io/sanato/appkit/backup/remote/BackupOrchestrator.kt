package io.sanato.appkit.backup.remote

import io.sanato.appkit.backup.archive.ArchiveBuilder
import io.sanato.appkit.backup.archive.ArchiveReader
import io.sanato.appkit.backup.core.BackupDataSource
import io.sanato.appkit.backup.core.BackupLogger
import io.sanato.appkit.backup.core.BackupProgress
import io.sanato.appkit.backup.core.BackupRestoreTarget
import io.sanato.appkit.backup.core.PassphraseProvider
import io.sanato.appkit.backup.core.ProgressListener
import io.sanato.appkit.backup.core.TransferPhase
import io.sanato.appkit.backup.format.BackupAlgorithms
import io.sanato.appkit.backup.format.BackupCodec
import io.sanato.appkit.backup.format.Sbk1BackupCodec
import io.sanato.appkit.backup.format.SealOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 备份/恢复的安全关键编排：媒体先行、上传后回读校验、校验通过才清理旧文件、media 永不删、
 * 新整包就位才轮换旧的。对 [RemoteBackupStore] 编程，SAF 与 Google Drive 共用同一套编排；
 * 单测用内存假实现（见 `testing.InMemoryRemoteBackupStore`）。
 *
 * 五条不变式：
 * 1. **媒体先行**——[writeSnapshot]/[writeArchiveBundle] 在快照/整包上传之前，先把引用到的
 *    媒体全部确认在远端 media 库（[ensureMediaUploaded]），媒体没传齐绝不写快照。
 * 2. **上传后回读校验**——[verifySnapshotOrThrow] 重新拉取远端文件列表确认快照真的落地
 *    且非空、引用的媒体确实都在，而不是相信本地"upload() 没抛异常"就算数。
 * 3. **校验通过才清理**——只清理"本地记录仍存在、且已被本次快照覆盖"的旧 entries；
 *    本地记录已被宿主删除的孤儿 entry 故意保留（它可能是该记录唯一的备份）。
 * 4. **media 永不删**——[pruneByName] 只作用于 snapshots/bundles 两个文件夹。
 * 5. **新整包就位才轮换旧的**——[writeArchiveBundle] 校验通过、保留策略清理之前，旧 bundle
 *    始终还在，不存在"旧的已删、新的还没传完"的中间状态。
 *
 * @param sealedSuffix 上传到远端的文件后缀，默认 `.sdb`。⚠️ 格式身份只看容器内的 magic
 * 字节，不看文件名后缀——不要改这个默认值：远端可能已经有大量历史文件用这个后缀命名，
 * 改了后缀会让现有清理/保留逻辑既找不到旧文件、也覆盖不了它们。
 * @param ioDispatcher 编排逻辑运行所在的调度器，默认 [Dispatchers.IO]；公开这个构造参数
 * 是为了测试能注入 `UnconfinedTestDispatcher`。
 */
public class BackupOrchestrator(
    private val store: RemoteBackupStore,
    private val dataSource: BackupDataSource,
    private val restoreTarget: BackupRestoreTarget,
    private val sealPassphrase: PassphraseProvider,
    private val unsealPassphrases: List<PassphraseProvider>,
    private val workDir: File,
    private val codec: BackupCodec = Sbk1BackupCodec(),
    private val logger: BackupLogger = BackupLogger.None,
    private val sealedSuffix: String = ".sdb",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    public var snapshotRetention: Int = 30
    public var bundleRetention: Int = 2
    public var maxMediaUploadRetry: Int = 3

    /** 日常保存：媒体先行 → 写该条记录的仅清单 entry。返回实际备份条数（0 = 本 dataSource 查无此条，未上传）。 */
    public suspend fun backupEntryDelta(recordId: String): Int =
        withContext(ioDispatcher) {
            val record = dataSource.loadRecord(recordId) ?: return@withContext 0
            val tmpZip = File(workDir, "bk_entry_${System.nanoTime()}.zip")
            try {
                val built =
                    ArchiveBuilder.build(
                        outputZip = tmpZip,
                        payloadSchema = dataSource.payloadSchema,
                        payloadSchemaVersion = dataSource.payloadSchemaVersion,
                        producer = "",
                        createdAtMillis = System.currentTimeMillis(),
                        records = listOf(record),
                        includeMediaBytes = false,
                        resolveMedia = dataSource::resolveLocalMedia,
                    )
                ensureMediaUploaded(built.mediaFiles, null)
                val sealed = File(workDir, "bk_entry_seal_${System.nanoTime()}$sealedSuffix")
                try {
                    codec.seal(
                        tmpZip,
                        sealed,
                        sealPassphrase,
                        SealOptions(payloadProfile = BackupAlgorithms.PROFILE_MANIFEST_ONLY),
                    )
                    store.upload(RemoteBackupStore.FOLDER_ENTRIES, entryName(recordId), sealed)
                } finally {
                    sealed.delete()
                }
                1
            } finally {
                tmpZip.delete()
            }
        }

    /**
     * 写一份全量清单快照。媒体先行 → 上传快照 → 回读校验 → 校验通过才清理被覆盖的 entries →
     * 按 [snapshotRetention] 保留最近 N 份快照。
     * @param snapshotTimeMillis 快照逻辑时间（用于命名与"覆盖"判定；调用方传入以保证可测的确定性）。
     */
    public suspend fun writeSnapshot(
        snapshotTimeMillis: Long,
        onProgress: ProgressListener? = null,
    ): Int =
        withContext(ioDispatcher) {
            val tmpZip = File(workDir, "snapshot_${System.nanoTime()}.zip")
            try {
                val summaries = dataSource.listRecordSummaries()
                val records = summaries.mapNotNull { dataSource.loadRecord(it.id) }
                if (records.isEmpty()) {
                    check(summaries.isEmpty()) { "导出 0 篇但本地有记录，快照未执行，不清理" }
                    return@withContext 0
                }
                val built =
                    ArchiveBuilder.build(
                        outputZip = tmpZip,
                        payloadSchema = dataSource.payloadSchema,
                        payloadSchemaVersion = dataSource.payloadSchemaVersion,
                        producer = "",
                        createdAtMillis = snapshotTimeMillis,
                        records = records,
                        includeMediaBytes = false,
                        resolveMedia = dataSource::resolveLocalMedia,
                    )
                ensureMediaUploaded(built.mediaFiles, onProgress) // 不变式①：媒体先行

                val snapshotName = snapshotName(snapshotTimeMillis)
                val sealed = File(workDir, "snapshot_seal_${System.nanoTime()}$sealedSuffix")
                try {
                    codec.seal(
                        tmpZip,
                        sealed,
                        sealPassphrase,
                        SealOptions(payloadProfile = BackupAlgorithms.PROFILE_MANIFEST_ONLY),
                    )
                    store.upload(RemoteBackupStore.FOLDER_SNAPSHOTS, snapshotName, sealed)
                } finally {
                    sealed.delete()
                }

                // 不变式②：回读校验——快照存在且非空，且引用的每个媒体都在 media 库且非空。
                verifySnapshotOrThrow(snapshotName, built.mediaFiles.map { it.name })

                // 不变式③：校验过才清理——只清"本地仍存在且已被本快照覆盖"的 entry；
                // 本地已删记录对应的孤儿 entry 故意保留，它可能是该记录唯一的备份。
                val coveredIds = summaries.filter { it.modifiedAtMillis <= snapshotTimeMillis }.map { it.id }.toSet()
                store
                    .list(RemoteBackupStore.FOLDER_ENTRIES)
                    .filter { rf -> coveredIds.any { rf.name == entryName(it) } }
                    .forEach { rf ->
                        runCatching { store.delete(rf.id) }
                            .onFailure { logger.warn("prune entry failed: ${rf.name}", it) }
                    }

                // 不变式④(隐含)：pruneByName 只作用于 snapshots/bundles，从不碰 media。
                pruneByName(RemoteBackupStore.FOLDER_SNAPSHOTS, SNAPSHOT_PREFIX, snapshotRetention)
                records.size
            } finally {
                tmpZip.delete()
            }
        }

    /** 低频自包含整包（含媒体，零依赖兜底）。保留最近 [bundleRetention] 份；media 永不删。 */
    public suspend fun writeArchiveBundle(bundleTimeMillis: Long): Int =
        withContext(ioDispatcher) {
            val tmpZip = File(workDir, "bundle_${System.nanoTime()}.zip")
            try {
                val summaries = dataSource.listRecordSummaries()
                val records = summaries.mapNotNull { dataSource.loadRecord(it.id) }
                if (records.isEmpty()) {
                    check(summaries.isEmpty()) { "导出 0 篇但本地有记录，整包未执行" }
                    return@withContext 0
                }
                ArchiveBuilder.build(
                    outputZip = tmpZip,
                    payloadSchema = dataSource.payloadSchema,
                    payloadSchemaVersion = dataSource.payloadSchemaVersion,
                    producer = "",
                    createdAtMillis = bundleTimeMillis,
                    records = records,
                    includeMediaBytes = true,
                    resolveMedia = dataSource::resolveLocalMedia,
                )
                val name = bundleName(bundleTimeMillis)
                val sealed = File(workDir, "bundle_seal_${System.nanoTime()}$sealedSuffix")
                try {
                    codec.seal(
                        tmpZip,
                        sealed,
                        sealPassphrase,
                        SealOptions(payloadProfile = BackupAlgorithms.PROFILE_FULL),
                    )
                    store.upload(RemoteBackupStore.FOLDER_BUNDLES, name, sealed)
                } finally {
                    sealed.delete()
                }
                // 校验整包存在且非空（零依赖兜底必须真实落盘才算数）。
                check(store.list(RemoteBackupStore.FOLDER_BUNDLES).any { it.name == name && it.size > 0 }) {
                    "整包校验失败：$name 不存在或为空"
                }
                // 不变式⑤：留最近 N 份；仅当更新且校验过的整包就位后才轮换旧的。media 不在清理范围。
                pruneByName(RemoteBackupStore.FOLDER_BUNDLES, BUNDLE_PREFIX, bundleRetention)
                records.size
            } finally {
                tmpZip.delete()
            }
        }

    /**
     * 恢复：取最新快照（+media 库按需补齐）与最新整包中较新者为基底导入，再叠加全部 entries；
     * bundle 仅在无快照、或快照路径一篇都没恢复出来时才兜底下载。全程 suspend，media 补齐
     * 不存在任何 runBlocking 桥接。
     * @return 累计导入条数（含叠加 entries，幂等去重靠宿主的 [BackupRestoreTarget.accept] 收敛）。
     */
    public suspend fun restore(onProgress: ProgressListener? = null): Int =
        withContext(ioDispatcher) {
            try {
                var restored = 0
                // 整轮只列一次远端 media 库，按 name 建索引——修复了"每补一个媒体就 list()
                // 一次"的 O(N²) 现存缺陷。
                val remoteMediaIndex =
                    runCatching {
                        store.list(RemoteBackupStore.FOLDER_MEDIA).filter { it.size > 0 }.associateBy { it.name }
                    }.getOrDefault(emptyMap())

                val latestSnapshot =
                    store
                        .list(RemoteBackupStore.FOLDER_SNAPSHOTS)
                        .filter { it.name.startsWith(SNAPSHOT_PREFIX) }
                        .maxByName()
                val latestBundle =
                    store
                        .list(RemoteBackupStore.FOLDER_BUNDLES)
                        .filter { it.name.startsWith(BUNDLE_PREFIX) }
                        .maxByName()

                // 优先快照(+media 库)：快照小、媒体按需补；整包(自包含、可能数百 MB)仅作零依赖
                // 兜底，只在"无快照"或"快照路径一篇都没恢复出来"时才下载。
                val primary = latestSnapshot ?: latestBundle
                primary?.let {
                    restored +=
                        if (it === latestSnapshot) {
                            importRemote(it.id, remoteMediaIndex, onProgress)
                        } else {
                            importRemote(it.id, null, null)
                        }
                }
                for (e in store.list(RemoteBackupStore.FOLDER_ENTRIES)) {
                    restored += importRemote(e.id, remoteMediaIndex, null)
                }
                if (latestSnapshot != null && restored == 0 && latestBundle != null) {
                    restored += importRemote(latestBundle.id, null, null)
                }
                restored
            } finally {
                sweepWorkDir()
            }
        }

    private suspend fun importRemote(
        fileId: String,
        remoteMediaIndex: Map<String, RemoteFile>?,
        onProgress: ProgressListener?,
    ): Int {
        val sealed = File(workDir, "restore_dl_${System.nanoTime()}$sealedSuffix")
        val plainZip = File(workDir, "restore_${System.nanoTime()}.zip")
        return try {
            store.download(fileId, sealed)
            codec.unseal(sealed, plainZip, unsealPassphrases)
            importArchive(plainZip, remoteMediaIndex, onProgress)
        } finally {
            sealed.delete()
            plainZip.delete()
        }
    }

    private suspend fun importArchive(
        zipFile: File,
        remoteMediaIndex: Map<String, RemoteFile>?,
        onProgress: ProgressListener?,
    ): Int {
        val mediaDir = restoreTarget.mediaDirectory()
        val parsed = ArchiveReader.extract(zipFile, mediaDir, dataSource.payloadSchema, logger)
        restoreTarget.onRestoreStart(parsed.manifest.header)

        val neededNames =
            parsed.manifest.records
                .flatMap { it.mediaNames }
                .distinct()
        val toDownload =
            if (remoteMediaIndex != null) {
                neededNames.filter { name -> name !in parsed.extractedMedia && !hasLocalMedia(mediaDir, name) }
            } else {
                emptyList()
            }
        if (onProgress != null && toDownload.isNotEmpty()) {
            onProgress.onProgress(BackupProgress(TransferPhase.DOWNLOADING, 0, toDownload.size))
        }
        var downloaded = 0

        var accepted = 0
        for (record in parsed.manifest.records) {
            val resolvedMedia = mutableMapOf<String, File>()
            for (name in record.mediaNames) {
                val embedded = parsed.extractedMedia[name]
                val local = if (embedded == null) localMediaFileOrNull(mediaDir, name) else null
                val file =
                    embedded ?: local ?: remoteMediaIndex?.let { idx -> downloadAndUnsealMedia(name, idx, mediaDir) }
                if (file != null) {
                    resolvedMedia[name] = file
                    if (embedded == null && local == null) {
                        downloaded++
                        onProgress?.onProgress(BackupProgress(TransferPhase.DOWNLOADING, downloaded, toDownload.size))
                    }
                }
            }
            if (restoreTarget.accept(record, resolvedMedia)) accepted++
        }
        restoreTarget.onRestoreFinish(accepted)
        return accepted
    }

    /** 从 media 库下载 `<name><sealedSuffix>` 并解密到媒体目录；补不到返回 null（文本照常导入，绝不因媒体缺失而失败）。 */
    private suspend fun downloadAndUnsealMedia(
        name: String,
        remoteMediaIndex: Map<String, RemoteFile>,
        mediaDir: File,
    ): File? {
        val remote = remoteMediaIndex[name + sealedSuffix] ?: return null
        val sealed = File(workDir, "media_dl_${System.nanoTime()}$sealedSuffix")
        val plain = File(mediaDir, name)
        return try {
            store.download(remote.id, sealed)
            codec.unseal(sealed, plain, unsealPassphrases)
            plain
        } catch (e: Exception) {
            logger.warn("restore: media unseal failed: $name", e)
            null
        } finally {
            sealed.delete()
        }
    }

    private fun hasLocalMedia(
        mediaDir: File,
        name: String,
    ): Boolean = localMediaFileOrNull(mediaDir, name) != null

    private fun localMediaFileOrNull(
        mediaDir: File,
        name: String,
    ): File? = File(mediaDir, name).takeIf { it.isFile && it.length() > 0L }

    /**
     * 把单个媒体文件 seal 后 upload-if-absent 进 media 库。整轮只列一次远端 media 库，
     * 只上传缺失的；进度总数 = 待传数（已传过的不计入进度、也不重传）。
     */
    private suspend fun ensureMediaUploaded(
        mediaFiles: List<File>,
        onProgress: ProgressListener?,
    ) {
        val present =
            runCatching {
                store
                    .list(RemoteBackupStore.FOLDER_MEDIA)
                    .filter { it.size > 0 }
                    .map { it.name }
                    .toHashSet()
            }.getOrDefault(hashSetOf())
        val missing = mediaFiles.filter { (it.name + sealedSuffix) !in present }
        val total = missing.size
        if (onProgress != null && total > 0) onProgress.onProgress(BackupProgress(TransferPhase.UPLOADING, 0, total))
        for ((i, mf) in missing.withIndex()) {
            uploadOneMediaWithRetry(mf)
            onProgress?.onProgress(BackupProgress(TransferPhase.UPLOADING, i + 1, total))
        }
    }

    /** 上传单个媒体失败自动重试（移动网络易抖）；已传的由 uploadIfAbsent 跳过，重试不会重复上传成功的文件。 */
    private suspend fun uploadOneMediaWithRetry(mf: File) {
        var attempt = 0
        while (true) {
            val sealed = File(workDir, "media_${System.nanoTime()}$sealedSuffix")
            try {
                codec.seal(
                    mf,
                    sealed,
                    sealPassphrase,
                    SealOptions(
                        contentType = BackupAlgorithms.CONTENT_OPAQUE_BLOB,
                        payloadProfile = BackupAlgorithms.PROFILE_MEDIA,
                    ),
                )
                store.uploadIfAbsent(RemoteBackupStore.FOLDER_MEDIA, mf.name + sealedSuffix, sealed)
                return
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxMediaUploadRetry) {
                    logger.warn("media upload gave up after $attempt tries: ${mf.name}", e)
                    throw e
                }
                logger.warn(
                    "media upload failed (try $attempt/$maxMediaUploadRetry) ${mf.name}: ${e.message} — retrying",
                )
                delay(1500L * attempt) // 退避：1.5s、3s
            } finally {
                sealed.delete()
            }
        }
    }

    private suspend fun verifySnapshotOrThrow(
        snapshotName: String,
        mediaNames: List<String>,
    ) {
        val snapOk = store.list(RemoteBackupStore.FOLDER_SNAPSHOTS).any { it.name == snapshotName && it.size > 0 }
        check(snapOk) { "快照校验失败：$snapshotName 不存在或为空" }
        val present =
            store
                .list(RemoteBackupStore.FOLDER_MEDIA)
                .filter { it.size > 0 }
                .map { it.name }
                .toSet()
        val missing = mediaNames.map { it + sealedSuffix }.filter { it !in present }
        check(missing.isEmpty()) { "快照校验失败：媒体缺失=$missing" }
    }

    /**
     * 同名前缀文件按名降序保留最近 [keep] 份，其余删除。
     *
     * ⚠️ 失败方向修复：名字解析失败的文件（临时文件、其它客户端写的名字、未来的新命名）
     * **永不删除**，只记一条 warn——旧实现对解析失败的文件返回 `Long.MIN_VALUE` 排最后，
     * 结果是"解析不了 = 优先被删"，方向反了，是一条真实的数据丢失路径。
     */
    private suspend fun pruneByName(
        folder: String,
        prefix: String,
        keep: Int,
    ) {
        val all = store.list(folder).filter { it.name.startsWith(prefix) }
        val (parsable, unparsable) = all.partition { timestampOf(it.name, prefix) != null }
        if (unparsable.isNotEmpty()) {
            logger.warn(
                "prune: ${unparsable.size} file(s) in $folder have unparsable names, " +
                    "never deleting them: ${unparsable.map { it.name }}",
            )
        }
        parsable
            .sortedByDescending { timestampOf(it.name, prefix)!! }
            .drop(keep)
            .forEach { rf ->
                runCatching { store.delete(rf.id) }
                    .onFailure { logger.warn("prune delete failed: ${rf.name}", it) }
            }
    }

    private fun sweepWorkDir() {
        workDir.listFiles()?.forEach { f ->
            val n = f.name
            if (n.startsWith("media_dl_") || n.startsWith("media_pl_") || n.startsWith("restore_")) {
                runCatching { f.delete() }
            }
        }
    }

    private fun entryName(recordId: String) = "$ENTRY_PREFIX$recordId$sealedSuffix"

    private fun snapshotName(timeMillis: Long) = "$SNAPSHOT_PREFIX$timeMillis$sealedSuffix"

    private fun bundleName(timeMillis: Long) = "$BUNDLE_PREFIX$timeMillis$sealedSuffix"

    /** 名字里前缀之后、第一个 `.` 之前的数字段——与后缀无关，兼容未来后缀变化。 */
    private fun timestampOf(
        name: String,
        prefix: String,
    ): Long? = name.removePrefix(prefix).substringBefore('.').toLongOrNull()

    // ⚠️ 按文件名字符串比较取最新：文件名是定长 epoch millis，字符串序恰好等价于时间序——
    // 这是一个隐式约定（沿用自迁移前的实现），不是通用的时间戳比较。
    private fun List<RemoteFile>.maxByName(): RemoteFile? = maxByOrNull { it.name }

    private companion object {
        const val SNAPSHOT_PREFIX = "snapshot_"
        const val BUNDLE_PREFIX = "bundle_"
        const val ENTRY_PREFIX = "entry_"
    }
}
