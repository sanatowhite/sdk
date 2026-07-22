package io.sanato.updatechecker

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider

internal class UpdateDownloadReceiver(
    private val info: UpdateInfo,
    private val downloadId: Long
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (completedId != downloadId) return

        context.unregisterReceiver(this)

        val file = ApkDownloader.downloadedFile(context, info)
        if (!file.exists() || !Sha256Verifier.matches(file, info.sha256)) {
            Toast.makeText(
                context,
                context.getString(R.string.updatechecker_verify_failed),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.versioncheck.fileprovider",
            file
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }
}
