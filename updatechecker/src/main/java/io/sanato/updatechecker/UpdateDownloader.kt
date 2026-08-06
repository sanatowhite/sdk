package io.sanato.updatechecker

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * 只新增文件,不改动任何现有公开签名(见 CLAUDE.md 的 :updatechecker 四条铁律)。
 * 包装 internal 的 [ApkDownloader],补上进度观察能力。
 *
 * 轮询的实现保证 collect 取消时自动停止、不泄漏——`delay()` 本身就是一个
 * 挂起点,协程取消会在下一次 `delay()` 处自然抛出并结束这个 flow,不需要
 * 额外注册/反注册任何监听器。
 */
class UpdateDownloader(
    private val context: Context,
) {
    /** 发起下载,持续 emit 进度直到 [UpdateDownloadState.ReadyToInstall] 或 [UpdateDownloadState.Failed]。 */
    fun download(info: UpdateInfo): Flow<UpdateDownloadState> =
        flow {
            val downloadId = ApkDownloader.enqueue(context, info)
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            emit(UpdateDownloadState.InProgress(bytesDownloaded = 0L, totalBytes = 0L))

            while (true) {
                val progress = queryProgress(downloadManager, downloadId)
                when (progress.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val file = ApkDownloader.downloadedFile(context, info)
                        emit(UpdateDownloadState.Verifying(file))
                        if (file.exists() && Sha256Verifier.matches(file, info.sha256)) {
                            emit(UpdateDownloadState.ReadyToInstall(file))
                        } else {
                            emit(UpdateDownloadState.Failed("SHA-256 verification failed"))
                        }
                        return@flow
                    }
                    DownloadManager.STATUS_FAILED -> {
                        emit(UpdateDownloadState.Failed("Download failed (status=${progress.status})"))
                        return@flow
                    }
                    else -> emit(UpdateDownloadState.InProgress(progress.bytesDownloaded, progress.totalBytes))
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }.distinctUntilChanged()

    /**
     * 引导用户安装已下载并校验通过的 APK。FileProvider authority 必须继续用
     * SDK 自己的 `${applicationId}.versioncheck.fileprovider`,不要误用宿主
     * app 的 authority——那是两个不同 `<provider>` 声明,用错会直接
     * `IllegalArgumentException`。
     */
    fun install(file: File) {
        val apkUri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.versioncheck.fileprovider",
                file,
            )
        val installIntent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(installIntent)
    }

    private fun queryProgress(
        downloadManager: DownloadManager,
        downloadId: Long,
    ): DownloadProgress {
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return DownloadProgress(DownloadManager.STATUS_RUNNING, 0L, 0L)
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return DownloadProgress(status, downloaded, total)
        }
    }

    private data class DownloadProgress(val status: Int, val bytesDownloaded: Long, val totalBytes: Long)

    private companion object {
        const val POLL_INTERVAL_MILLIS = 500L
    }
}
