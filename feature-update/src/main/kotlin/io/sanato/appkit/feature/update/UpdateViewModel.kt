package io.sanato.appkit.feature.update

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.updatechecker.UpdateChecker
import io.sanato.updatechecker.UpdateDownloadState
import io.sanato.updatechecker.UpdateDownloader
import io.sanato.updatechecker.UpdateInfo
import io.sanato.updatechecker.UpdateResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data object UpToDate : UpdateUiState

    data class Error(
        val message: String,
    ) : UpdateUiState

    data class Available(
        val info: UpdateInfo,
    ) : UpdateUiState

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : UpdateUiState

    data class ReadyToInstall(
        val file: File,
    ) : UpdateUiState

    data class DownloadFailed(
        val reason: String,
    ) : UpdateUiState
}

/**
 * 只用 `:updatechecker` 的公开 API(`UpdateChecker`/`UpdateDownloader`),
 * 粘合逻辑全在这个模块——SDK 本身不知道 Compose/Hilt 的存在。`configUrl` 来自
 * 注入的 [UpdateConfig](见 [UpdateConfigModule]),不是硬编码常量,消费方通过
 * [UpdateConfigOverride] 可选绑定覆盖。
 */
@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        application: Application,
        config: UpdateConfig,
    ) : ViewModel() {
        private val updateChecker = UpdateChecker(application, config.configUrl)
        private val updateDownloader = UpdateDownloader(application)

        private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
        val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

        fun checkForUpdate() {
            viewModelScope.launch {
                _uiState.value = UpdateUiState.Checking
                _uiState.value =
                    when (val result = updateChecker.check()) {
                        is UpdateResult.Available -> UpdateUiState.Available(result.info)
                        is UpdateResult.UpToDate -> UpdateUiState.UpToDate
                        is UpdateResult.Error -> UpdateUiState.Error(result.message)
                    }
            }
        }

        fun startDownload(info: UpdateInfo) {
            viewModelScope.launch {
                updateDownloader.download(info).collect { state ->
                    _uiState.value =
                        when (state) {
                            is UpdateDownloadState.Idle -> {
                                UpdateUiState.Idle
                            }

                            is UpdateDownloadState.InProgress -> {
                                UpdateUiState.Downloading(state.bytesDownloaded, state.totalBytes)
                            }

                            // 校验中沿用"下载中"展示,不需要给用户区分这个短暂的中间态。
                            is UpdateDownloadState.Verifying -> {
                                UpdateUiState.Downloading(0L, 0L)
                            }

                            is UpdateDownloadState.ReadyToInstall -> {
                                UpdateUiState.ReadyToInstall(state.file)
                            }

                            is UpdateDownloadState.Failed -> {
                                UpdateUiState.DownloadFailed(state.reason)
                            }
                        }
                }
            }
        }

        fun install(file: File) {
            updateDownloader.install(file)
        }

        fun dismiss() {
            _uiState.value = UpdateUiState.Idle
        }
    }
