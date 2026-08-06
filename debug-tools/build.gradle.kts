plugins {
    alias(libs.plugins.sanato.android.library.compose)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.debugtools"

    // 见 core-common/build.gradle.kts 的同一条注释：这个模块没有 src/testFixtures
    // 源码，关掉避免发布出一个空的 -test-fixtures.aar。
    testFixtures.enable = false
}

dependencies {
    // ⚠️ implementation(project(":core-common")) 和 implementation(project(":core-net"))
    // 都删掉——源码零引用(已确认)。这个模块唯一实际用到的类型是
    // core-telemetry 的 RingLogBuffer。

    // RingLogBuffer 是 DebugDrawer(ringLogBuffer: RingLogBuffer, ...) 的参数类型。
    api(project(":core-telemetry"))
    // @Composable 注解本身出现在 DebugDrawer 的公开签名里（content 参数是
    // @Composable () -> Unit）。BOM 同理需要 api，见 core-ui 的同一条注释。
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
}
