package io.sanato.updatechecker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal object UpdateCheckPrefs {
    private const val PREFS_NAME = "io.sanato.updatechecker.prefs"
    private const val KEY_LAST_CHECK_DATE = "last_auto_check_date"

    private fun dateFormat(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

    fun shouldAutoCheck(context: Context, todayMillis: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(KEY_LAST_CHECK_DATE, null) ?: return true
        return lastDate != dateFormat().format(todayMillis)
    }

    fun markChecked(context: Context, todayMillis: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_CHECK_DATE, dateFormat().format(todayMillis)).apply()
    }
}
