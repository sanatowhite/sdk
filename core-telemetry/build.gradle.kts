plugins {
    alias(libs.plugins.sanato.android.library)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.core.telemetry"

    // 见 core-common/build.gradle.kts 的同一条注释：这个模块没有 src/testFixtures
    // 源码，关掉避免发布出一个空的 -test-fixtures.aar。
    testFixtures.enable = false
}

dependencies {
    implementation(project(":core-common"))
    // StartupTrackerInitializer 等四个 initializer 实现类 implement
    // AppInitializer（超接口，必须 api）。
    api(project(":core-init"))

    implementation(libs.androidx.metrics.performance)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
