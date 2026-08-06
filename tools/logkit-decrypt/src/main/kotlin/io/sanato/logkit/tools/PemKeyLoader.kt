package io.sanato.logkit.tools

import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** 只认 PKCS#8 PEM(`openssl pkcs8 -topk8 -nocrypt` 或 `ecparam ... | openssl pkcs8 -topk8` 的输出)。 */
internal object PemKeyLoader {
    fun loadPrivateKey(file: File): PrivateKey {
        val text = file.readText(Charsets.UTF_8)
        val base64 =
            text
                .lineSequence()
                .filterNot { it.startsWith("-----") }
                .joinToString("")
                .trim()
        val der = Base64.getDecoder().decode(base64)
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der))
    }
}
