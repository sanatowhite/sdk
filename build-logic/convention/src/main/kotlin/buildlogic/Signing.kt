package buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project
import java.util.Properties

/**
 * 三级读取优先级：CI 环境变量 > keystore.properties > 都没有。
 *
 * 【设计红线】没有签名文件时【必须】静默降级到 debug 签名，否则任何人 fork 下来
 * 第一次 `assembleRelease` 就编译不过——这是模板类项目最常见的"开箱即挂"。
 *
 * 用 keystore.properties 而不是 local.properties：后者由 Android Studio 托管
 * sdk.dir，混进密钥容易被误提交/覆盖。
 */
internal fun Project.configureSigning(extension: ApplicationExtension) {
    val props =
        Properties().apply {
            val f = rootProject.file("keystore.properties")
            if (f.exists()) f.inputStream().use(::load)
        }

    fun read(
        propKey: String,
        envKey: String,
    ): String? =
        props.getProperty(propKey)?.takeIf(String::isNotBlank)
            ?: System.getenv(envKey)?.takeIf(String::isNotBlank)

    val storePath = read("store.file", "STORE_FILE")
    val storePassword = read("store.password", "STORE_PASSWORD")
    val keyAlias = read("key.alias", "KEY_ALIAS")
    val keyPassword = read("key.password", "KEY_PASSWORD")
    val storeFile = storePath?.let { rootProject.file(it) }

    if (storeFile != null && storeFile.exists() && storePassword != null &&
        keyAlias != null && keyPassword != null
    ) {
        extension.signingConfigs.create("release") {
            this.storeFile = storeFile
            this.storePassword = storePassword
            this.keyAlias = keyAlias
            this.keyPassword = keyPassword
        }
        logger.lifecycle("[signing] release keystore: ${storeFile.name}")
    } else {
        logger.warn(
            "[signing] no release keystore found -> release/staging will be signed with " +
                "the DEBUG key. Provide keystore.properties (store.file/store.password/" +
                "key.alias/key.password) or env STORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD.",
        )
    }
}
