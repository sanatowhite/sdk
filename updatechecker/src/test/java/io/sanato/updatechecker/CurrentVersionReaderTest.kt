package io.sanato.updatechecker

import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CurrentVersionReaderTest {
    @Test
    fun `reads the host app's own long version code`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = PackageInfo().apply {
            packageName = context.packageName
            setLongVersionCode(42L)
        }
        shadowOf(context.packageManager).installPackage(packageInfo)

        val result = CurrentVersionReader.read(context)

        assertEquals(42L, result)
    }
}
