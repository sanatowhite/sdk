package io.sanato.updatechecker

internal interface ConfigFetcher {
    fun fetch(configUrl: String): String
}
