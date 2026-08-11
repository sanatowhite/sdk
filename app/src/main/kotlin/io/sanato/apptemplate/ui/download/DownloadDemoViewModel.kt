package io.sanato.apptemplate.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.appkit.download.DownloadRequest
import io.sanato.appkit.download.DownloadState
import io.sanato.appkit.download.DownloadTask
import io.sanato.appkit.download.Downloader
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * OVH's public speed-test file — a well-known, Range-friendly (plain Apache
 * static file) test payload with no auth/quota, widely used for exactly this
 * kind of bandwidth demo. Same reasoning as `ui/websocket/WebSocketDemoViewModel`'s
 * choice of the Postman echo server: this template has no real backend of its
 * own, so the demo needs a real public endpoint rather than mocking one.
 * A fork should point this at its own file host.
 */
private const val SAMPLE_DOWNLOAD_URL = "https://proof.ovh.net/files/10Mb.dat"

/**
 * `:downloadkit`'s `Downloader` is a plain (non-Hilt) singleton by design
 * (see its KDoc) — `:downloadkit-hilt`'s `DownloadModule` is what makes it
 * constructor-injectable here. This ViewModel does no orchestration of its
 * own; it only forwards [Downloader.tasks] and click handlers.
 */
@HiltViewModel
class DownloadDemoViewModel
    @Inject
    constructor(
        private val downloader: Downloader,
    ) : ViewModel() {
        val tasks: StateFlow<List<DownloadTask>> =
            downloader.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), downloader.tasks.value)

        /** Each click enqueues a distinct task (unique file name) — repeatable, and demonstrates `maxConcurrent` queuing once a few are in flight. */
        fun enqueueSample() {
            downloader.enqueue(DownloadRequest(url = SAMPLE_DOWNLOAD_URL, fileName = "sample-${System.nanoTime()}.dat"))
        }

        fun pause(id: String) = downloader.pause(id)

        fun resume(id: String) = downloader.resume(id)

        fun cancel(id: String) = downloader.cancel(id)

        fun cancelAll() = downloader.cancelAll()
    }

internal fun DownloadState.isResumable(): Boolean = this is DownloadState.Paused || this is DownloadState.Failed

internal fun DownloadState.isPausable(): Boolean = this is DownloadState.Running
