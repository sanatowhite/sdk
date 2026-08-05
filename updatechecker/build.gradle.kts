plugins {
    id("com.android.library")
    `maven-publish`
    // 刻意没有 org.jetbrains.kotlin.android —— AGP 9 内置 Kotlin，
    // KGP 版本由根 build.gradle.kts 的 buildscript classpath 提供。
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

// 显式声明 publication，不再依赖 JitPack 自动注入的 init script。
// artifactId 固定为 "updatechecker"：JitPack 检测到 ~/.m2 里最终只有这一个
// artifact 时，会把发布坐标重命名为仓库名 version-check-sdk——这正是现有
// 消费方 com.github.sanatowhite:version-check-sdk 坐标得以保住的机制。
// groupId / version 留空，继承 project.group / project.version；
// jitpack.yml 传的 -Pgroup=$GROUP -Pversion=$VERSION 正好写进这两个属性。
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                artifactId = "updatechecker"
            }
        }
    }
}

dependencies {
    // ⚠️ 刻意保持这两个版本不升级（1.15.0 / 1.9.0），即使仓库其余部分已经用更新的版本。
    // 这是发布出去的库，依赖版本越低，对消费方的传递依赖约束越小；升级留到发 SDK
    // v1.1.0 时再单独评估，不要和这次纯粹的 AGP/Gradle 工具链升级混在一起验证。
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
