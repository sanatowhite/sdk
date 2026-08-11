package io.sanato.appkit.download

import androidx.test.core.app.ApplicationProvider
import io.sanato.appkit.core.net.HttpClientFactory
import io.sanato.appkit.core.net.RetryInterceptor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DownloaderTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun config() = DownloadConfig(downloadDir = tempFolder.newFolder(), notificationsEnabled = false)

    @After
    fun tearDown() {
        // See Downloader.resetForTesting's KDoc — without this, tests sharing a
        // Robolectric sandbox would leak each other's Downloader singleton.
        Downloader.resetForTesting()
    }

    @Test
    fun `getInstance returns the same instance on every call for this process`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val first = Downloader.getInstance(context, config())
        val second = Downloader.getInstance(context, config())

        assertSame(first, second)
    }

    @Test
    fun `downloadOkHttpClient zeroes out call and read timeouts`() {
        val base = HttpClientFactory.okHttpClient()

        val client = Downloader.downloadOkHttpClient(base)

        assertEquals(0, client.callTimeoutMillis)
        assertEquals(0, client.readTimeoutMillis)
    }

    @Test
    fun `downloadOkHttpClient strips core-net's blocking RetryInterceptor`() {
        val base = HttpClientFactory.okHttpClient()
        assertTrue(base.interceptors.any { it is RetryInterceptor })

        val client = Downloader.downloadOkHttpClient(base)

        assertFalse(client.interceptors.any { it is RetryInterceptor })
    }

    @Test
    @Config(sdk = [32])
    fun `hasNotificationPermission is always true below API 33`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val downloader = Downloader.getInstance(context, config())

        assertTrue(downloader.hasNotificationPermission())
    }

    @Test
    fun `enqueue returns a stable id and the task shows up in tasks`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val downloader = Downloader.getInstance(context, config())

        val id = downloader.enqueue(DownloadRequest(url = "https://example.invalid/f", fileName = "f.bin"))

        assertEquals(taskIdFor("https://example.invalid/f", "f.bin"), id)
        // enqueue() only sends a command into DownloadQueue's channel — the loop
        // coroutine (a real dispatcher here, not runTest's virtual scheduler) applies
        // it asynchronously, so this polls rather than asserting immediately.
        runBlocking {
            withTimeout(5_000) {
                while (downloader.tasks.value.none { it.id == id }) {
                    delay(10)
                }
            }
        }
    }
}
