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

        // Download IDs are a small, guessable, monotonically-increasing shared counter, not a
        // secret, and this receiver is RECEIVER_EXPORTED so any app on the device can send a
        // spoofed/premature DOWNLOAD_COMPLETE broadcast carrying the right ID. Re-confirm the
        // real status via DownloadManager itself before trusting the broadcast: if it's not
        // actually STATUS_SUCCESSFUL yet, keep listening instead of unregistering, so the genuine
        // completion broadcast (which will arrive later) is not missed.
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val isSuccessful = downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            cursor.moveToFirst() &&
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
        }
        if (!isSuccessful) return

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
