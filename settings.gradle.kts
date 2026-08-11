// ────────────────────────────────────────────────────────────────
// JitPack 多模块发布模式
//
// JitPack 跑的是根级 `publishToMavenLocal`（见 jitpack.yml）；本仓库以
// com.github.sanatowhite.sdk:<module>:<version> 的形式发布【多个】模块——这正是
// 想要的：JitPack 的坐标规则是"~/.m2 里恰好 1 个 artifact → com.github.User:Repo；
// 多个 → com.github.User.Repo:Module"，多模块发布本来就需要落进第二条规则。
// 旧的"只留 1 个 artifact 保住单一历史坐标"（`com.github.sanatowhite:version-check-sdk`）
// 的顾虑已随本次改造作废：仓库改名 + 老坐标不再维护，见 docs/adr（原 0001，
// 已被取代）。
//
// 仍然需要 gate 的是【不该发布的模块】：:app（签名/google-services/
// aboutlibraries/baselineprofile 插件，对发布链路纯粹是失败面）、:benchmark、
// :baselineprofile（两者都靠 targetProjectPath 指向 :app，脱离 :app 无意义）。
//
// JITPACK=true 是 jitpack.yml 里显式声明的环境变量（本地可用
// `JITPACK=true ./gradlew publishSdkToMavenLocal -Pversion=probe` 复现）。
//
// ⚠️ 实测踩过的坑：`pluginManagement {}` 块被 Gradle 当成独立片段提前编译，
// 看不到在它之前声明的普通顶层 val（哪怕就在同一个文件里），报
// "Unresolved reference"。所以这里没有共享变量，`pluginManagement` 内和
// 文件后半段的判断表达式各自内联。
// ────────────────────────────────────────────────────────────────
pluginManagement {
    // 无条件 includeBuild —— 每个可发布模块现在都要用到 build-logic 提供的
    // sanato.android.library.published / sanato.api.check 两个新 convention
    // plugin（含 :updatechecker，它虽然不用 sanato.android.library，但这两个
    // 是纯 mix-in，见 CLAUDE.md 的铁律调整）。"JITPACK 模式跳过 build-logic"
    // 这条旧优化的前提（:updatechecker 是唯一存活模块且完全不碰 convention
    // plugin）不再成立。
    includeBuild("build-logic")
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

rootProject.name = "sdk"

// pluginManagement 之后的代码可以正常使用顶层 val（见上方注释里的坑）。
val jitpack = System.getenv("JITPACK") == "true"

// ── 可发布模块：任何模式下都 include ──────────────────────────────
// 这份清单和根 build.gradle.kts 的 sdkModules 必须一致——两处漂移由
// `verifySdkModuleList` 任务机械检查。Phase 8-9 陆续加入 feature-* 系列时，
// 两处要同步更新。
//
// :telemetry-firebase 无条件 include——它自己不 apply google-services/
// crashlytics 插件（那两个插件的强约束是 applicationId 与 google-services.json
// 匹配，只能挂在 :app），只提供 FirebaseTelemetryModule 的 Hilt 绑定 + Firebase
// SDK 依赖，编译期不需要真实的 google-services.json。现在它是 :app 的默认
// 遥测后端（见 app/build.gradle.kts），不再有 telemetryFirebaseEnabled 这个
// 开关。
include(
    ":updatechecker",
    ":backupkit",
    ":backupkit-drive",
    ":core-common",
    ":core-common-hilt",
    ":core-init",
    ":core-init-hilt",
    ":core-ui",
    ":core-net",
    ":core-data",
    ":core-data-hilt",
    ":core-telemetry",
    ":core-telemetry-hilt",
    ":net-telemetry-hilt",
    ":core-auth",
    ":auth-firebase",
    ":auth-net-hilt",
    ":debug-tools",
    ":telemetry-firebase",
    ":feature-settings",
    ":feature-feedback",
    ":feature-licenses",
    ":feature-update",
    ":feature-auth",
    ":sdk-bom",
)

// ── 不可发布模块：JitPack 上一律排除 ──────────────────────────────
if (!jitpack) {
    include(
        ":app",
        ":benchmark",
        ":baselineprofile",
        // :logkit 和 :updatechecker 同一条铁律(零内部依赖、零三方运行时依赖、
        // 不套 convention plugin),但刻意不放进上面 include(":updatechecker")
        // 那一行、不套 maven-publish——本期不对外发布。原因见 jitpack.yml 与
        // ADR-0001:JitPack 检测到 ~/.m2 里只有一个 artifact 才会把坐标改写成
        // 仓库名;第二个已发布 artifact 会让 JitPack 切到多模块坐标规则,把现有
        // 消费方的 com.github.sanatowhite:version-check-sdk 坐标变成聚合 POM。
        // :logkit 若将来要发布,走 ADR-0001 的 subtree 镜像到独立单模块仓库,
        // 绝不作为第二个 artifact 加进本仓库的 JitPack 坐标。
        ":logkit",
        // 纯 JVM 工具,离线解密 .logkit 文件；与 :logkit 共享同一份
        // io.sanato.logkit.format 源码(见其 build.gradle.kts 的 srcDir),
        // 零 project 依赖边。同样 gate 在 JITPACK 路径之外：它套用
        // org.jetbrains.kotlin.jvm,JITPACK 路径刻意跳过 includeBuild("build-logic")
        // 以保持最小配置面,不该为这个工具破例。
        ":logkit-decrypt",
    )
    project(":logkit-decrypt").projectDir = file("tools/logkit-decrypt")
}
