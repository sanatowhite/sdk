package io.sanato.appkit.backup.format

import io.sanato.appkit.backup.core.PassphraseProvider
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * [BackupCodec] 的唯一实现：写当前的 SBK1 格式，读 SBK1 + 三种只读的历史格式
 * （见 [LegacySdbCodec]）。
 */
public class Sbk1BackupCodec : BackupCodec {
    override fun inspect(sealedFile: File): BackupContainerInfo {
        val legacy = LegacySdbCodec.detect(sealedFile)
        if (legacy != null && !isSbk1(sealedFile)) {
            return BackupContainerInfo(
                formatVersion = 0,
                cipherId = 0,
                macId = 0,
                kdfId = 0,
                kdfIterations = 0,
                contentType = BackupAlgorithms.CONTENT_ARCHIVE_ZIP,
                payloadProfile = BackupAlgorithms.PROFILE_FULL,
                createdAtMillis = -1L,
                plaintextLength = -1L,
                producer = "",
                isLegacyFormat = true,
            )
        }
        RandomAccessFile(sealedFile, "r").use { raf ->
            val header = ContainerHeaderCodec.decode(raf, sealedFile.path)
            return BackupContainerInfo(
                formatVersion = header.formatVersion,
                cipherId = header.cipherId,
                macId = header.macId,
                kdfId = header.kdfId,
                kdfIterations = header.kdfIterations,
                contentType = header.contentType,
                payloadProfile = header.payloadProfile,
                createdAtMillis = header.createdAtWallMillis,
                plaintextLength = header.plaintextLength,
                producer = header.producer,
                isLegacyFormat = false,
            )
        }
    }

    override suspend fun seal(
        plainFile: File,
        outputFile: File,
        passphrase: PassphraseProvider,
        options: SealOptions,
    ) {
        val secret = passphrase.passphrase()
        var master: ByteArray? = null
        try {
            val salt = ByteArray(BackupAlgorithms.SALT_SIZE).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(BackupAlgorithms.IV_SIZE).also { SecureRandom().nextBytes(it) }
            master =
                KeySchedule.deriveMasterKey(secret, salt, options.kdfIterations, BackupAlgorithms.KEY_SIZE)
            val encKey = KeySchedule.deriveEncKey(master)
            val macKey = KeySchedule.deriveMacKey(master)

            val headerBytes =
                ContainerHeaderCodec.encode(
                    cipherId = BackupAlgorithms.CIPHER_AES256_CTR,
                    macId = BackupAlgorithms.MAC_HMAC_SHA256,
                    kdfId = BackupAlgorithms.KDF_PBKDF2_HMAC_SHA256,
                    contentType = options.contentType,
                    payloadProfile = options.payloadProfile,
                    kdfIterations = options.kdfIterations,
                    saltLength = BackupAlgorithms.SALT_SIZE,
                    ivLength = BackupAlgorithms.IV_SIZE,
                    macLength = BackupAlgorithms.MAC_SIZE,
                    keyLength = BackupAlgorithms.KEY_SIZE,
                    createdAtWallMillis = System.currentTimeMillis(),
                    plaintextLength = plainFile.length(),
                    producer = options.producer,
                    salt = salt,
                    iv = iv,
                )

            StreamCipherPipeline.seal(headerBytes, plainFile, outputFile, encKey, macKey, iv, BackupAlgorithms.MAC_SIZE)
        } finally {
            master?.let(KeySchedule::wipe)
            KeySchedule.wipe(secret)
        }
    }

    override suspend fun unseal(
        sealedFile: File,
        outputFile: File,
        passphrases: List<PassphraseProvider>,
    ) {
        require(passphrases.isNotEmpty()) { "unseal() needs at least one PassphraseProvider candidate" }
        val path = sealedFile.path

        if (isSbk1(sealedFile)) {
            val header = RandomAccessFile(sealedFile, "r").use { ContainerHeaderCodec.decode(it, path) }
            var lastError: BackupFormatException.AuthenticationFailed? = null
            for (provider in passphrases) {
                val secret = provider.passphrase()
                var master: ByteArray? = null
                try {
                    master = KeySchedule.deriveMasterKey(secret, header.salt, header.kdfIterations, header.keyLength)
                    val encKey = KeySchedule.deriveEncKey(master)
                    val macKey = KeySchedule.deriveMacKey(master)
                    StreamCipherPipeline.unseal(sealedFile, header, outputFile, encKey, macKey, path)
                    return
                } catch (e: BackupFormatException.AuthenticationFailed) {
                    lastError = e
                } finally {
                    master?.let(KeySchedule::wipe)
                    KeySchedule.wipe(secret)
                }
            }
            throw lastError ?: BackupFormatException.AuthenticationFailed(path)
        }

        when (val legacy = LegacySdbCodec.detect(sealedFile)) {
            LegacySdbCodec.LegacyFormat.PLAIN_ZIP -> {
                sealedFile.copyTo(outputFile, overwrite = true)
                return
            }

            LegacySdbCodec.LegacyFormat.SDB1_GCM, LegacySdbCodec.LegacyFormat.SDB2_CTR -> {
                var lastError: BackupFormatException.AuthenticationFailed? = null
                for (provider in passphrases) {
                    val secret = provider.passphrase()
                    try {
                        if (legacy == LegacySdbCodec.LegacyFormat.SDB1_GCM) {
                            LegacySdbCodec.decodeSdb1(sealedFile, outputFile, secret, path)
                        } else {
                            LegacySdbCodec.decodeSdb2(sealedFile, outputFile, secret, path)
                        }
                        return
                    } catch (e: BackupFormatException.AuthenticationFailed) {
                        lastError = e
                    } finally {
                        KeySchedule.wipe(secret)
                    }
                }
                throw lastError ?: BackupFormatException.AuthenticationFailed(path)
            }

            null -> {
                throw BackupFormatException.NotABackupFile(path)
            }
        }
    }

    private fun isSbk1(file: File): Boolean {
        if (file.length() < 4) return false
        val magic = ByteArray(4)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(0)
            raf.readFully(magic)
        }
        return String(magic, Charsets.US_ASCII) == BackupAlgorithms.MAGIC
    }
}
