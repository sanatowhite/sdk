package io.sanato.updatechecker

import java.net.HttpURLConnection
import java.net.URL

internal class HttpConfigFetcher : ConfigFetcher {
    override fun fetch(configUrl: String): String {
        val connection = URL(configUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "HTTP ${connection.responseCode}"
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
