package io.sanato.apptemplate.about

import android.content.Context
import androidx.annotation.RawRes
import io.sanato.apptemplate.R
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
 * `res/raw/changelog.json` 是单一真源——同时供关于页、What's New 弹窗消费,
 * CI 发版时也从这份文件生成远端 JSON 的 `releaseNotes`(Phase 10/11)。
 */
object ChangelogReader {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(
        context: Context,
        @RawRes rawResId: Int = R.raw.changelog,
    ): List<ChangelogEntry> {
        val text =
            context.resources
                .openRawResource(rawResId)
                .bufferedReader()
                .use { it.readText() }
        return json.decodeFromString<ChangelogFile>(text).entries
    }
}
