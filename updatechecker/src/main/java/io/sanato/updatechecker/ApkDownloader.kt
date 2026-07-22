package io.sanato.updatechecker

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

internal object ApkDownloader {
    fun enqueue(context: Context, info: UpdateInfo): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle(context.getString(R.string.updatechecker_download_title, info.versionName))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, "apk_updates", targetFileName(info))
            .setAllowedOverMetered(true)

        return downloadManager.enqueue(request)
    }

    fun targetFileName(info: UpdateInfo): String = "update-${info.versionCode}.apk"

    fun downloadedFile(context: Context, info: UpdateInfo): File =
        File(context.getExternalFilesDir("apk_updates"), targetFileName(info))
}
