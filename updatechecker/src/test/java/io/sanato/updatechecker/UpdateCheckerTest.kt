package io.sanato.updatechecker

import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateCheckerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun installCurrentVersion(versionCode: Long) {
        val packageInfo =
            PackageInfo().apply {
                packageName = context.packageName
                setLongVersionCode(versionCode)
            }
        shadowOf(context.packageManager).installPackage(packageInfo)
    }

    @Test
    fun `returns Available when remote version is newer`() =
        runTest {
            installCurrentVersion(6L)
            val fetcher =
                object : ConfigFetcher {
                    override fun fetch(configUrl: String) =
                        """
                        {
                          "versionCode": 7,
                          "versionName": "1.0.1",
                          "apkUrl": "https://example.com/app.apk",
                          "sha256": "abc",
                          "releaseNotes": "notes",
                          "force": false
                        }
                        """.trimIndent()
                }
            val checker = UpdateChecker(context, "https://example.com/version.json", fetcher)

            val result = checker.check()

            assertTrue(result is UpdateResult.Available)
            assertEquals(7L, (result as UpdateResult.Available).info.versionCode)
        }

    @Test
    fun `returns UpToDate when remote version is not newer`() =
        runTest {
            installCurrentVersion(7L)
            val fetcher =
                object : ConfigFetcher {
                    override fun fetch(configUrl: String) =
                        """{"versionCode": 7, "versionName": "1.0.1", "apkUrl": "x", "sha256": "abc", "force": false}"""
                }
            val checker = UpdateChecker(context, "https://example.com/version.json", fetcher)

            val result = checker.check()

            assertEquals(UpdateResult.UpToDate, result)
        }

    @Test
    fun `returns Error when fetch throws`() =
        runTest {
            installCurrentVersion(6L)
            val fetcher =
                object : ConfigFetcher {
                    override fun fetch(configUrl: String): String = throw IOException("network down")
                }
            val checker = UpdateChecker(context, "https://example.com/version.json", fetcher)

            val result = checker.check()

            assertTrue(result is UpdateResult.Error)
        }

    @Test
    fun `does not throttle future auto checks after a network error`() =
        runTest {
            installCurrentVersion(6L)
            val fetcher =
                object : ConfigFetcher {
                    override fun fetch(configUrl: String): String = throw IOException("network down")
                }
            val checker = UpdateChecker(context, "https://example.com/version.json", fetcher)

            checker.check()

            assertTrue(UpdateChecker.shouldAutoCheck(context))
        }
}
