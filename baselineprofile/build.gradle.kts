// 骨架模块——实际的 BaselineProfileGenerator 测试类在 Phase 11 补上。
// 这里只把拓扑和插件接线打通，供 Phase 3 的多模块 probe 验证。
plugins {
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.sanato.apptemplate.baselineprofile"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        // BaselineProfileRule 要求设备 API >= 28（app 本身 minSdk 仍是 24）。
        minSdk = 28
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
}
