package io.sanato.appkit.backup.format

/** SBK1 头部字段的内部载体。见 README 的容器格式规范表。 */
internal class ContainerHeader(
    val formatVersion: Int,
    val cipherId: Int,
    val macId: Int,
    val kdfId: Int,
    val headerLength: Int,
    val contentType: Int,
    val payloadProfile: Int,
    val kdfIterations: Int,
    val saltLength: Int,
    val ivLength: Int,
    val macLength: Int,
    val keyLength: Int,
    val createdAtWallMillis: Long,
    val plaintextLength: Long,
    val producer: String,
    val salt: ByteArray,
    val iv: ByteArray,
)
