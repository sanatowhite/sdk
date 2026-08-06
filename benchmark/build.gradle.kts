// 骨架模块——实际的 StartupBenchmark / ScrollBenchmark 测试类在 Phase 11 补上。
plugins {
    id("com.android.test")
}

android {
    namespace = "io.sanato.apptemplate.benchmark"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk = 28
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
}
