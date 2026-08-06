package io.sanato.apptemplate.update

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.logkit.LogKit
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
 * 粘合逻辑全在 `:app`——SDK 本身不知道 Compose/Hilt 的存在。
 */
@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        application: Application,
    ) : ViewModel() {
        private val updateChecker = UpdateChecker(application, UPDATE_CONFIG_URL)
        private val updateDownloader = UpdateDownloader(application)

        private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
        val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

        fun checkForUpdate() {
            viewModelScope.launch {
                LogKit.i("Update", "checking")
                _uiState.value = UpdateUiState.Checking
                _uiState.value =
                    when (val result = updateChecker.check()) {
                        is UpdateResult.Available -> {
                            LogKit.i("Update", "available versionCode=${result.info.versionCode}")
                            UpdateUiState.Available(result.info)
                        }

                        is UpdateResult.UpToDate -> {
                            LogKit.i("Update", "up to date")
                            UpdateUiState.UpToDate
                        }

                        is UpdateResult.Error -> {
                            LogKit.w("Update", "check failed: ${result.message}")
                            UpdateUiState.Error(result.message)
                        }
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
                                // 刻意不记:每个下载进度块记一条会在几秒内 churn 掉
                                // 整个 5MB 日志预算,把想留住的崩溃现场挤出去——
                                // 这是最容易不小心炸掉预算的一处,别在这里加日志。
                                UpdateUiState.Downloading(state.bytesDownloaded, state.totalBytes)
                            }

                            // 校验中沿用"下载中"展示,不需要给用户区分这个短暂的中间态。
                            is UpdateDownloadState.Verifying -> {
                                UpdateUiState.Downloading(0L, 0L)
                            }

                            is UpdateDownloadState.ReadyToInstall -> {
                                LogKit.i("Update", "ready to install: ${state.file.name}")
                                UpdateUiState.ReadyToInstall(state.file)
                            }

                            is UpdateDownloadState.Failed -> {
                                LogKit.w("Update", "download failed: ${state.reason}")
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
