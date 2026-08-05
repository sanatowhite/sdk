package buildlogic

import org.gradle.api.Project
import java.io.File
import java.util.Properties

internal data class AppVersion(
    val versionCode: Int,
    val versionName: String,
)

/**
 * 版本号的单一真源是 `gradle/version.properties`，不从 git tag 推导。
 *
 * 理由：AGP 9 移除了 `applicationVariants`/`variantFilter` 等旧变体 API，
 * 唯一剩下的 variant 级写入点是 `androidComponents.onVariants { variant.outputs... }`，
 * 但那里 set 的 versionCode 不会回写进 `BuildConfig.VERSION_CODE`（BuildConfig 的值
 * 来自 defaultConfig）——会出现"APK manifest 是 1002、BuildConfig 却是 1"这种
 * 静默不一致。而读一个 properties 文件是 3 行 shell，CI 和本地行为完全一致，
 * 不依赖任何 AGP 内部产物。
 */
internal fun Project.readAppVersion(): AppVersion {
    val file = rootProject.file("gradle/version.properties")
    if (!file.exists()) return AppVersion(versionCode = 1, versionName = "0.0.1-nogit")
    val props = Properties().apply { file.inputStream().use(::load) }
    val name = props.getProperty("versionName") ?: "0.0.1-nogit"
    val code = props.getProperty("versionCode")?.toIntOrNull() ?: 1
    return AppVersion(code, name)
}
