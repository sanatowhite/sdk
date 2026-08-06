package io.sanato.updatechecker

object VersionCompare {
    fun isNewerVersion(
        remoteVersionCode: Long,
        currentVersionCode: Long,
    ): Boolean = remoteVersionCode > currentVersionCode
}
