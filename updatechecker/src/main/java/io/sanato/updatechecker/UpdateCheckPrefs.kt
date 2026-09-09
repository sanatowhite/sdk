package io.sanato.updatechecker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal object UpdateCheckPrefs {
    private const val PREFS_NAME = "io.sanato.updatechecker.prefs"
    private const val KEY_LAST_CHECK_DATE = "last_auto_check_date"
    private const val KEY_SKIPPED_VERSION_CODE = "skipped_version_code"
    private const val NO_SKIPPED_VERSION = -1L

    private fun dateFormat(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

    fun shouldAutoCheck(
        context: Context,
        todayMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(KEY_LAST_CHECK_DATE, null) ?: return true
        return lastDate != dateFormat().format(todayMillis)
    }

    fun markChecked(
        context: Context,
        todayMillis: Long = System.currentTimeMillis(),
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_CHECK_DATE, dateFormat().format(todayMillis)).apply()
    }

    fun skippedVersionCode(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getLong(KEY_SKIPPED_VERSION_CODE, NO_SKIPPED_VERSION)
        return value.takeIf { it != NO_SKIPPED_VERSION }
    }

    fun skipVersion(
        context: Context,
        versionCode: Long,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_SKIPPED_VERSION_CODE, versionCode).apply()
    }
}
