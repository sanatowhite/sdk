package io.sanato.updatechecker

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateChecker internal constructor(
    private val context: Context,
    private val configUrl: String,
    private val fetcher: ConfigFetcher
) {
    constructor(context: Context, configUrl: String) : this(
        context.applicationContext,
        configUrl,
        HttpConfigFetcher()
    )

    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        val result: UpdateResult = try {
            val json = fetcher.fetch(configUrl)
            val info = UpdateConfigParser.parse(json)
            val currentVersionCode = CurrentVersionReader.read(context)
            if (VersionCompare.isNewerVersion(info.versionCode, currentVersionCode)) {
                UpdateResult.Available(info)
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            return@withContext UpdateResult.Error(e.message ?: "Unknown error")
        }
        UpdateCheckPrefs.markChecked(context)
        result
    }

    companion object {
        fun shouldAutoCheck(context: Context): Boolean = UpdateCheckPrefs.shouldAutoCheck(context)

        fun showUpdateDialog(activity: android.app.Activity, info: UpdateInfo) {
            UpdateDialogPresenter.show(activity, info)
        }
    }
}
