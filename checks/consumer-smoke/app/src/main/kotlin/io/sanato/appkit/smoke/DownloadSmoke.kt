package io.sanato.appkit.smoke

import io.sanato.appkit.download.DownloadRequest
import io.sanato.appkit.download.DownloadState
import io.sanato.appkit.download.Downloader
import javax.inject.Inject

/**
 * 逐个引用 `:downloadkit`/`:downloadkit-hilt` 的公开入口——最容易判定错误的
 * `api`/`implementation` 边界是 `Downloader`（由 `:downloadkit-hilt` 的
 * `DownloadModule` 提供，构造期内部再拿 `:core-net` 的 `HttpClientFactory`
 * 组装 `OkHttpClient`，消费方自己完全不需要感知 OkHttp 的存在）。任何一条
 * 判定错误，这个文件就会出现 unresolved reference，编译直接失败。
 *
 * 只做编译期验证：不真的调用 `enqueue`（会拉起 `notify.DownloadService`，
 * 这个 smoke 工程只跑 `assembleDebug`，从不启动，没有意义也没必要）。
 * `DownloadState` 只用来验证 sealed 层级本身可解析。
 */
class DownloadSmoke
    @Inject
    constructor(
        private val downloader: Downloader,
    ) {
        fun currentTasks() = downloader.tasks.value

        fun describeState(state: DownloadState): String = state::class.java.simpleName

        fun sampleRequest(): DownloadRequest = DownloadRequest(url = "https://example.invalid/f", fileName = "f.bin")
    }
