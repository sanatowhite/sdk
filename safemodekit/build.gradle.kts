plugins {
    id("com.android.library")
    // 刻意没有 sanato.android.library——理由同 backupkit/build.gradle.kts：那个 convention
    // plugin 会注入非预期依赖和配置，改变发布产物。下面两个是纯叠加的 mix-in。
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.safemode"
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

    // Java 11——对外发布的库，与其他发布模块统一口径。
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // SafeMode.authenticate(activity: FragmentActivity, ...) 把 FragmentActivity 放进了公开签名，
    // 按 ADR 0009 必须 api；FragmentActivity 经 biometric 的 api 依赖传递过来。
    // 版本取最低可用的稳定版，减少对消费方的传递依赖约束。
    api("androidx.biometric:biometric:1.1.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    testImplementation("junit:junit:4.13.2")
}
