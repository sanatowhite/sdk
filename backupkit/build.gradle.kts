plugins {
    id("com.android.library")
    // 刻意没有 org.jetbrains.kotlin.android —— AGP 9 内置 Kotlin，KGP 版本由根
    // build.gradle.kts 的 buildscript classpath 提供。
    // 刻意没有 sanato.android.library —— 那个 convention plugin 会注入
    // core-ktx/coroutines-android/javax-inject/testFixtures 等非预期依赖和配置，
    // 改变发布产物。下面两个是纯叠加的 mix-in，见 updatechecker/build.gradle.kts
    // 的同款注释。
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.backup"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        // targetSdk 不设：AGP 9 已弃用 library 模块的 targetSdk。
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // Java 11——对外发布的库，消费方可能还在更旧的 AGP/JDK 上，与 updatechecker/logkit
    // 统一口径。
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // 不写 kotlinOptions {} —— 内置 Kotlin 下已失效，jvmTarget 跟随
    // compileOptions.targetCompatibility。

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // BackupOrchestrator 的公开构造函数带一个 `ioDispatcher: CoroutineDispatcher = Dispatchers.IO`
    // 参数（用于测试注入 UnconfinedTestDispatcher，也是唯一的公开可配置项）——
    // CoroutineDispatcher 出现在公开签名里，按 ADR 0009 必须是 api，不是"因为有 suspend
    // 函数"（suspend 本身只用 kotlin-stdlib 的 Continuation，不需要这条依赖）。
    // 刻意保持版本不升级（1.9.0，不用 libs.versions.toml 的 1.11.0）：这是发布出去的库，
    // 依赖版本越低对消费方的传递依赖约束越小，与 updatechecker 同一条原则。
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("io.mockk:mockk:1.14.11")
    // 生产代码里 org.json.JSONObject 来自 android.jar（编译期即有，无需声明依赖）；
    // 纯 JVM 单测跑在 mockable android.jar 上，JSONObject 方法默认全部抛异常，
    // 需要真实实现——与 updatechecker 的测试依赖同一个道理。
    testImplementation("org.json:json:20231013")
}
