import java.util.Properties

// sdk-bom 是纯 `java-platform` 模块,不是 Android library——不能 apply
// sanato.android.library.published(那个插件用 pluginManager.withPlugin(
// "com.android.library") 挂钩,这里永远不会触发,group/version/publication
// 全部不会生效)。build-logic 的 readSdkCoordinates() 又是 internal(故意不
// 对外暴露),从这个不同 Gradle 模块的脚本编译单元里够不到——与其为唯一一个
// 消费者放宽可见性,不如把 gradle/version.properties 的读取逻辑原样内联在这里,
// 这个模块本来就该是自包含的(java-platform 没有 Kotlin 源码,不需要任何
// convention plugin)。
plugins {
    `java-platform`
    `maven-publish`
}

val versionProps =
    Properties().apply {
        rootProject.file("gradle/version.properties").inputStream().use(::load)
    }

group = versionProps.getProperty("sdkGroup") ?: error("gradle/version.properties is missing sdkGroup=")
version =
    version.toString().takeUnless { it == "unspecified" }
        ?: versionProps.getProperty("sdkVersion")
        ?: error("gradle/version.properties is missing sdkVersion=")

// 约束所有发布模块的版本对齐——消费方 implementation(platform("$group:sdk-bom:$version"))
// 之后各模块坐标就不用再写版本号。这份清单和根 build.gradle.kts 的 sdkModules
// (去掉 sdk-bom 自己)必须一致,漂移由 verifySdkBomConstraints 任务机械检查。
dependencies {
    constraints {
        listOf(
            "updatechecker",
            "backupkit",
            "backupkit-drive",
            "core-common",
            "core-common-hilt",
            "core-init",
            "core-init-hilt",
            "core-ui",
            "core-net",
            "core-data",
            "core-data-hilt",
            "core-telemetry",
            "core-telemetry-hilt",
            "net-telemetry-hilt",
            "core-auth",
            "auth-firebase",
            "auth-net-hilt",
            "debug-tools",
            "telemetry-firebase",
            "feature-settings",
            "feature-feedback",
            "feature-licenses",
            "feature-update",
            "feature-auth",
        ).forEach { module -> api("$group:$module:$version") }
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["javaPlatform"])
        }
    }
}
