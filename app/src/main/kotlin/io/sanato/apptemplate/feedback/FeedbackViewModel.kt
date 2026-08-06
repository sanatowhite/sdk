package io.sanato.apptemplate.feedback

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.apptemplate.BuildConfig
import io.sanato.apptemplate.R
import io.sanato.apptemplate.core.telemetry.RingLogBuffer
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/** 不自建后端——反馈就是本地拼一封邮件,交给用户手机上已装的邮件客户端发送。 */
@HiltViewModel
class FeedbackViewModel
    @Inject
    constructor(
        private val application: Application,
        private val ringLogBuffer: RingLogBuffer,
    ) : ViewModel() {
        fun sendFeedback(
            description: String,
            screenshot: Bitmap?,
            includeLogs: Boolean,
        ) {
            val attachments = mutableListOf<Uri>()

            if (screenshot != null) {
                val file = File(application.cacheDir, "feedback_screenshot.png")
                FileOutputStream(file).use { out -> screenshot.compress(Bitmap.CompressFormat.PNG, 100, out) }
                attachments += fileUri(file)
            }

            if (includeLogs) {
                val file = File(application.cacheDir, "feedback_logs.txt")
                file.writeText(ringLogBuffer.snapshot().joinToString("\n"))
                attachments += fileUri(file)
            }

            val body =
                buildString {
                    appendLine(description)
                    appendLine()
                    append(DeviceInfoProvider.snapshot())
                }

            val sendIntent =
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        application.getString(
                            R.string.feedback_email_subject,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.GIT_SHA,
                        ),
                    )
                    putExtra(Intent.EXTRA_TEXT, body)
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachments))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            application.startActivity(Intent.createChooser(sendIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        private fun fileUri(file: File): Uri =
            FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", file)
    }
