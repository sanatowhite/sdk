package io.sanato.updatechecker

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

internal object CurrentVersionReader {
    fun read(context: Context): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }
}
