package io.sanato.appkit.feature.settings

import android.content.Context
import androidx.annotation.RawRes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChangelogEntry(
    val versionCode: Int,
    val versionName: String,
    val date: String,
    val highlights: List<String>,
)

@Serializable
private data class ChangelogFile(
    val entries: List<ChangelogEntry>,
)

/**
 * 消费方通过 `StandardPagesContent.changelogRawRes` 提供自己的更新日志 JSON——
 * 同时供关于页、What's New 弹窗消费。格式:
 * `{ "entries": [{ "versionCode": 1, "versionName": "1.0.0", "date": "2026-01-01",
 * "highlights": ["..."] }] }`
 */
object ChangelogReader {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(
        context: Context,
        @RawRes rawResId: Int,
    ): List<ChangelogEntry> {
        val text =
            context.resources
                .openRawResource(rawResId)
                .bufferedReader()
                .use { it.readText() }
        return json.decodeFromString<ChangelogFile>(text).entries
    }
}
