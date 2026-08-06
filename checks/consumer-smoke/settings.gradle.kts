// ────────────────────────────────────────────────────────────────
// 完全独立的 Gradle build——刻意不被根 settings.gradle.kts include。
//
// 为什么必须独立:如果这个工程是根 build 的一个子项目,Gradle 的
// project substitution 会把 `com.github.sanatowhite.sdk:core-net` 这种坐标
// 依赖悄悄换回同一个 build 里的 `project(":core-net")`——这样"验证发布出去的
// 坐标能不能被真正消费"这件事就是假的,任何 api()/implementation 判定错误都
// 会被 project() 依赖的透明性掩盖过去,编译永远不会失败。独立 build 没有这个
// substitution 机制,是唯一能捕获这类错误的手段。
//
// 两种模式:
// - 本地模式(默认):`mavenLocal()`——先在仓库根目录跑
//   `./gradlew publishSdkToMavenLocal -Pversion=X`,再到这里跑
//   `./gradlew :app:assembleDebug -PsmokeVersion=X`。
// - CI 远程模式(`-PsmokeRemote=true`):`https://jitpack.io`——验证 tag 发布后
//   真实坐标能不能被外部消费方解析,这是本地模式测不出来的(mavenLocal 只证明
//   "构建产物长这样",不证明"JitPack 真的把它们发出去了")。
// ────────────────────────────────────────────────────────────────
val smokeRemote = settings.startParameter.projectProperties["smokeRemote"] == "true"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        if (smokeRemote) {
            maven("https://jitpack.io")
        } else {
            mavenLocal()
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "consumer-smoke"
include(":app")
