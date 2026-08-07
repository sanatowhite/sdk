package io.sanato.appkit.backup.format

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-CTR 流式加解密 + MAC，恒定内存（Conscrypt 的 CTR 模式没有 GCM 那种内部
 * 整体缓冲，明文大小只受磁盘限制）。
 *
 * **写：单遍**——边加密边喂 `Mac.update`，最后把 header 和 MAC 一起拼上去。MAC 覆盖
 * `header || ciphertext` 的全部字节，这是把 magic/算法标识也纳入认证范围的关键：
 * legacy 格式的 HMAC 只盖 `salt‖iv‖ciphertext`，头部字段完全不受保护，篡改
 * `cipherId`/`kdfIterations` 不会被发现。
 *
 * **读：两遍**——先算 MAC 校验（fail-closed，通过之前不产出任何明文字节），再解密。
 */
internal object StreamCipherPipeline {
    private const val BUFFER_SIZE = 64 * 1024

    fun seal(
        headerBytes: ByteArray,
        plainFile: File,
        outputFile: File,
        encKey: SecretKeySpec,
        macKey: SecretKeySpec,
        iv: ByteArray,
        macLength: Int,
    ) {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encKey, IvParameterSpec(iv))
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(macKey)
        mac.update(headerBytes)

        val ciphertextTemp = File(outputFile.parentFile, outputFile.name + ".ct.tmp")
        try {
            FileOutputStream(ciphertextTemp).use { fos ->
                FileInputStream(plainFile).use { fis ->
                    val buf = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = fis.read(buf)
                        if (n <= 0) break
                        val encrypted = cipher.update(buf, 0, n)
                        if (encrypted != null && encrypted.isNotEmpty()) {
                            mac.update(encrypted)
                            fos.write(encrypted)
                        }
                    }
                    val finalBlock = cipher.doFinal()
                    if (finalBlock.isNotEmpty()) {
                        mac.update(finalBlock)
                        fos.write(finalBlock)
                    }
                }
            }
            val trailer = mac.doFinal()
            check(trailer.size == macLength) { "MAC length mismatch: ${trailer.size} != $macLength" }

            FileOutputStream(outputFile).use { fos ->
                fos.write(headerBytes)
                FileInputStream(ciphertextTemp).use { it.copyTo(fos, bufferSize = BUFFER_SIZE) }
                fos.write(trailer)
            }
        } finally {
            ciphertextTemp.delete()
        }
    }

    /**
     * @return 解密后写入 [outputFile]。MAC 不匹配抛 [BackupFormatException.AuthenticationFailed]。
     */
    fun unseal(
        sealedFile: File,
        header: ContainerHeader,
        outputFile: File,
        encKey: SecretKeySpec,
        macKey: SecretKeySpec,
        path: String,
    ) {
        val fileLength = sealedFile.length()
        val ciphertextStart = header.headerLength.toLong()
        val ciphertextEnd = fileLength - header.macLength
        if (ciphertextEnd < ciphertextStart) {
            throw BackupFormatException.Truncated(path)
        }

        // Pass 1：MAC 校验，覆盖 header || ciphertext。
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(macKey)
        RandomAccessFile(sealedFile, "r").use { raf ->
            val headerBytes = ByteArray(header.headerLength)
            raf.seek(0)
            raf.readFully(headerBytes)
            mac.update(headerBytes)

            raf.seek(ciphertextStart)
            var remaining = ciphertextEnd - ciphertextStart
            val buf = ByteArray(BUFFER_SIZE)
            while (remaining > 0) {
                val toRead = minOf(buf.size.toLong(), remaining).toInt()
                raf.readFully(buf, 0, toRead)
                mac.update(buf, 0, toRead)
                remaining -= toRead
            }
        }
        val computedMac = mac.doFinal()
        val storedMac = ByteArray(header.macLength)
        RandomAccessFile(sealedFile, "r").use { raf ->
            raf.seek(ciphertextEnd)
            raf.readFully(storedMac)
        }
        if (!MessageDigest.isEqual(computedMac, storedMac)) {
            throw BackupFormatException.AuthenticationFailed(path)
        }

        // Pass 2：CTR 解密。
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encKey, IvParameterSpec(header.iv))
        RandomAccessFile(sealedFile, "r").use { raf ->
            raf.seek(ciphertextStart)
            FileOutputStream(outputFile).use { fos ->
                var remaining = ciphertextEnd - ciphertextStart
                val buf = ByteArray(BUFFER_SIZE)
                while (remaining > 0) {
                    val toRead = minOf(buf.size.toLong(), remaining).toInt()
                    raf.readFully(buf, 0, toRead)
                    val decrypted = cipher.update(buf, 0, toRead)
                    if (decrypted != null && decrypted.isNotEmpty()) fos.write(decrypted)
                    remaining -= toRead
                }
                val finalBlock = cipher.doFinal()
                if (finalBlock.isNotEmpty()) fos.write(finalBlock)
            }
        }
    }
}
