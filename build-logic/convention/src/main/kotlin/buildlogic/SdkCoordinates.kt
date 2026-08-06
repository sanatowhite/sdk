package buildlogic

import org.gradle.api.Project
import java.util.Properties

internal data class SdkCoordinates(
    val group: String,
    val version: String,
)

/**
 * SDK 发布坐标的单一真源：`gradle/version.properties` 的 `sdkGroup` / `sdkVersion`。
 *
 * ⚠️ group 刻意【不】读 `-Pgroup`：Gradle 会把 `-Pgroup=X` 直接写进 `project.group`
 * （命令行属性覆盖同名 project 属性，这正是现在 `:updatechecker` 留空 groupId 也能
 * 工作的机制）。但 JitPack 注入的 `$GROUP` 是 `com.github.sanatowhite`，少了 `.sdk`
 * 后缀——多模块场景下用它会把 artifact 发到一个 JitPack 永远不会去查的路径。
 * 所以 group 只信文件，`jitpack.yml` 里也不再传 `-Pgroup`。
 *
 * version 相反：`-Pversion=$VERSION` 必须生效（tag 名就是 Maven version），
 * 文件里的 `sdkVersion` 只是本地 `publishToMavenLocal` 的兜底 + CI 断言的比对基准。
 */
internal fun Project.readSdkCoordinates(): SdkCoordinates {
    val file = rootProject.file("gradle/version.properties")
    val props = Properties().apply { if (file.exists()) file.inputStream().use(::load) }
    val group =
        props.getProperty("sdkGroup")
            ?: error("gradle/version.properties is missing sdkGroup=")

    // "unspecified" 是 Gradle 对未设置 version 的默认值；-Pversion 传进来时这里已经不是它了。
    val v =
        version.toString().takeUnless { it == Project.DEFAULT_VERSION }
            ?: props.getProperty("sdkVersion")
            ?: error("gradle/version.properties is missing sdkVersion=")

    return SdkCoordinates(group, v)
}
