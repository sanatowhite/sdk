plugins {
    id("com.android.library")
    // 刻意没有 org.jetbrains.kotlin.android —— AGP 9 内置 Kotlin，
    // KGP 版本由根 build.gradle.kts 的 buildscript classpath 提供。
    // 刻意没有 sanato.android.library —— 那个 convention plugin 会注入
    // core-ktx/coroutines-android/javax-inject/testFixtures 等非预期依赖和
    // 配置，改变发布产物。下面两个是纯叠加的 mix-in，不加任何依赖/不碰
    // defaultConfig，所以可以安全接入而不违反这条铁律的原意（见 CLAUDE.md）。
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.updatechecker"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        // targetSdk 不设：AGP 9 已弃用 library 模块的 targetSdk（且默认会跟随
        // compileSdk），继续声明只会多一条 deprecation 警告。
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // Java 保持 11（不是其余模块用的 17）：这个库仍在对外发布，消费方可能还在
    // 更旧的 AGP/JDK 上，11 字节码兼容面更宽。AGP 9 默认也已经是 11，这里显式
    // 写出来防止未来默认值再变。
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // 刻意不写 kotlinOptions {} —— 内置 Kotlin 下这个 DSL 已失效；jvmTarget 默认
    // 跟随上面的 compileOptions.targetCompatibility，官方原文："you don't need to
    // set jvmTarget because it defaults to android.compileOptions.targetCompatibility"。
}

// maven-publish 的 apply、singleVariant("release") { withSourcesJar() }、
// artifactId（默认取 project.name = "updatechecker"）、group/version（读
// gradle/version.properties 的 sdkGroup/sdkVersion，-Pversion 可覆盖）全部由
// sanato.android.library.published 提供——不再需要这个模块自己手写
// publishing{}/afterEvaluate{}。旧坐标 com.github.sanatowhite:version-check-sdk
// 的"只留一个 artifact 保坐标"机制已随本次改造作废（仓库改名 + 老坐标不再
// 维护），这个模块现在和其余模块一样发布到 com.github.sanatowhite.sdk:updatechecker。

dependencies {
    // kotlinx-coroutines-android 必须 api：UpdateDownloader.download() 返回
    // kotlinx.coroutines.flow.Flow<UpdateDownloadState>，消费方要能声明变量/
    // 链式调用就需要这个类型在编译期可解析——这是已经存在的 bug（消费方此前
    // 拿到的 AAR 缺它的编译期依赖），不是本次引入的，这次顺手修正。
    //
    // core-ktx 保持 implementation：javap 快照里 `UpdateCheckerFileProvider
    // extends androidx.core.content.FileProvider` 看着像 ABI 泄漏，但源码里
    // 这个类是 Kotlin `internal`——Kotlin 编译器会挡住消费方源码引用它（内部
    // 类在字节码层是 public 只是编译器实现细节，不代表可达）；它只通过
    // AndroidManifest.xml 的 <provider android:name="..."> 反射实例化，那是
    // 运行期 classpath 需求，implementation 的传递依赖已经满足。
    //
    // ⚠️ 刻意保持版本不升级（1.9.0），即使仓库其余部分已经用更新的版本。
    // 这是发布出去的库，依赖版本越低，对消费方的传递依赖约束越小；升级留到发 SDK
    // v1.1.0 时再单独评估，不要和这次纯粹的 AGP/Gradle 工具链升级混在一起验证。
    implementation("androidx.core:core-ktx:1.15.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

// apiDump / apiCheck 任务由 sanato.api.check 提供（见 build-logic 的
// ApiSnapshotTask + SanatoApiCheckConventionPlugin）——不再是这个模块自己手写
// 的 100 行 javap + golden 文本 diff。golden 文件路径不变，仍是
// api/updatechecker.api，但内容口径变了（修了两个缺陷：嵌套类/sealed 子类不再
// 被整体误滤、access$ 合成访问器行被过滤），第一次 apiDump 会是一次口径修正，
// 不是 API 破坏，见 Phase 5 提交说明。
