plugins {
    id("com.android.library")
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.backup.drive"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // :backupkit 提供 RemoteBackupStore 接口 + BackupOrchestrator + 容器格式，
    // 这个模块只加 Drive 的具体 RemoteBackupStore 实现——必须 api，因为消费方拿到
    // DriveBackupStore 就需要能声明 `RemoteBackupStore` 类型的变量。
    api(project(":backupkit"))

    // GMS Authorization API 是这个模块存在的唯一理由，但刻意 implementation：
    // 公开签名（DriveTokenProvider/DriveAuthResult/DriveBackupStore）里不出现任何
    // com.google.android.gms.* 类型——消费方永远不需要在自己代码里写出 GMS 类型名，
    // 这是刻意的设计约束，不是"恰好如此"（见 README "为什么这个模块可以带三方依赖"）。
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("io.mockk:mockk:1.14.11")
}
