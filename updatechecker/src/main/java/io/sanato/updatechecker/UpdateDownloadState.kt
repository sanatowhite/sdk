package io.sanato.updatechecker

import java.io.File

/**
 * [ApkDownloader] 是 internal 且没有进度——这是给消费方用的公开状态机,
 * 通过 [UpdateDownloader.download] 的 Flow 观察。
 */
sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()

    data class InProgress(val bytesDownloaded: Long, val totalBytes: Long) : UpdateDownloadState()

    data class Verifying(val file: File) : UpdateDownloadState()

    data class ReadyToInstall(val file: File) : UpdateDownloadState()

    data class Failed(val reason: String) : UpdateDownloadState()
}
