// ────────────────────────────────────────────────────────────────
// JitPack SDK-only 模式
//
// JitPack 跑的是根级 `publishToMavenLocal` + `tasks --all`（见 jitpack.yml 与
// 本仓库真实构建日志），会配置 settings 里 include 的【所有】模块。不 gate 的
// 后果：将来 :app 等模块的 Firebase / 签名 / google-services 会把这条发布链路
// 拖挂；且 ~/.m2 里出现多个 artifact 会让 JitPack 改用多模块坐标规则，把
// com.github.sanatowhite:version-check-sdk 这个历史坐标变成一个把所有模块都
// 当依赖的聚合 POM。
//
// JITPACK=true 是 jitpack.yml 里显式声明的环境变量（本地可用
// `JITPACK=true ./gradlew :updatechecker:publishToMavenLocal ...` 复现）。
//
// ⚠️ 实测踩过的坑：`pluginManagement {}` 块被 Gradle 当成独立片段提前编译，
// 看不到在它之前声明的普通顶层 val（哪怕就在同一个文件里），报
// "Unresolved reference"。所以这里没有共享变量，`pluginManagement` 内和
// 文件后半段的两处判断各自内联同一个表达式。
// ────────────────────────────────────────────────────────────────
pluginManagement {
    if (System.getenv("JITPACK") != "true") {
        // sdkOnly 时连 includeBuild("build-logic") 都跳过：build-logic 的编译
        // 本身是一个额外失败面，而 :updatechecker 刻意不使用任何 convention
        // plugin，完全不需要它。
        includeBuild("build-logic")
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "version-check-sdk"

include(":updatechecker") // 唯一在 sdkOnly 下存活的模块，永远保留

if (System.getenv("JITPACK") != "true") {
    include(
        ":app",
        ":core-common",
        ":core-ui",
        ":core-net",
        ":core-data",
        ":core-telemetry",
        ":debug-tools",
        ":benchmark",
        ":baselineprofile",
    )

    // :telemetry-firebase 只在 gradle.properties 的 telemetryFirebaseEnabled=true
    // 时才参与构建——关闭时这个模块连编译都不会被触发，是真正的零 Firebase 依赖，
    // 不是"依赖还在只是不生效"。开关同时控制 :app 是否 implementation(project(...))
    // 它（见 app/build.gradle.kts），两处必须保持一致。
    if (providers.gradleProperty("telemetryFirebaseEnabled").getOrElse("false") == "true") {
        include(":telemetry-firebase")
    }
}
