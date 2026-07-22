package io.sanato.updatechecker

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent

internal object UpdateDialogPresenter {
    fun show(activity: Activity, info: UpdateInfo) {
        val builder = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.updatechecker_dialog_title, info.versionName))
            .setMessage(info.releaseNotes)
            .setCancelable(!info.force)
            .setPositiveButton(R.string.updatechecker_update_now) { _, _ ->
                startDownload(activity, info)
            }

        if (!info.force) {
            builder.setNegativeButton(R.string.updatechecker_remind_later, null)
        }

        val dialog = builder.create()
        if (info.force) {
            dialog.setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }
        }
        dialog.show()
    }

    private fun startDownload(activity: Activity, info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            val uri = Uri.parse("package:${activity.packageName}")
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri))
            return
        }

        val downloadId = ApkDownloader.enqueue(activity, info)
        registerDownloadReceiver(activity, UpdateDownloadReceiver(info, downloadId))
    }

    private fun registerDownloadReceiver(activity: Activity, receiver: UpdateDownloadReceiver) {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            activity.registerReceiver(receiver, filter)
        }
    }
}
