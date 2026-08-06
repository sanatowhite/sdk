package io.sanato.updatechecker

import org.json.JSONObject

object UpdateConfigParser {
    class MalformedConfigException(
        message: String,
    ) : Exception(message)

    fun parse(json: String): UpdateInfo {
        val obj =
            try {
                JSONObject(json)
            } catch (e: Exception) {
                throw MalformedConfigException("Invalid JSON: ${e.message}")
            }
        if (!obj.has("versionCode") || !obj.has("versionName") || !obj.has("apkUrl") ||
            !obj.has("sha256") || !obj.has("force")
        ) {
            throw MalformedConfigException("Missing required field in update config")
        }
        return UpdateInfo(
            versionCode = obj.getLong("versionCode"),
            versionName = obj.getString("versionName"),
            apkUrl = obj.getString("apkUrl"),
            sha256 = obj.getString("sha256"),
            releaseNotes = obj.optString("releaseNotes", ""),
            force = obj.getBoolean("force"),
        )
    }
}
