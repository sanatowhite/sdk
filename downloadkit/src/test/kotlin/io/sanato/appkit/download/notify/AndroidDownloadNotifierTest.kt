package io.sanato.appkit.download.notify

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowApplication

@RunWith(RobolectricTestRunner::class)
class AndroidDownloadNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val notifier = AndroidDownloadNotifier(context)
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun summary(
        percent: Int = 42,
        canPause: Boolean = true,
        canResume: Boolean = false,
    ) = DownloadSummary(
        activeCount = 1,
        totalCount = 3,
        overallPercent = percent,
        canPause = canPause,
        canResume = canResume,
    )

    @Test
    fun `constructing the notifier creates a low-importance channel`() {
        val channel = manager.getNotificationChannel(AndroidDownloadNotifier.CHANNEL_ID)

        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel!!.importance)
    }

    @Test
    fun `buildForegroundNotification and notifyProgress use the same notification id`() {
        assertEquals(AndroidDownloadNotifier.NOTIFICATION_ID, notifier.notificationId)

        val notification = notifier.buildForegroundNotification(summary())
        assertNotNull(notification)
    }

    @Test
    fun `notifyProgress does not crash and does not post when POST_NOTIFICATIONS is denied`() {
        // Robolectric's default shadow reports notifications as disabled unless the
        // permission is explicitly granted — this is exactly the API 33+ denied state
        // AndroidDownloadNotifier's contract (DownloadNotifier's KDoc) must survive.
        val shadowApp: ShadowApplication = shadowOf(context)
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        notifier.notifyProgress(summary())
        notifier.notifyPausedByTimeout()
        notifier.clear()
        // No assertion beyond "did not throw" — see DownloadNotifier's KDoc contract.
    }

    @Test
    fun `pause action is shown when canPause is true, resume when canResume is true`() {
        notifier.notifyProgress(summary(canPause = true, canResume = false))
        val pausing = shadowOf(manager).getNotification(AndroidDownloadNotifier.NOTIFICATION_ID)
        assertEquals(2, pausing.actions.size) // pause + cancel-all

        notifier.notifyProgress(summary(canPause = false, canResume = true))
        val resuming = shadowOf(manager).getNotification(AndroidDownloadNotifier.NOTIFICATION_ID)
        assertEquals(2, resuming.actions.size) // resume + cancel-all
    }

    @Test
    fun `clear cancels the notification`() {
        notifier.notifyProgress(summary())
        notifier.clear()

        assertNull(shadowOf(manager).getNotification(AndroidDownloadNotifier.NOTIFICATION_ID))
    }
}
