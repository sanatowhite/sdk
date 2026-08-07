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
    // 刻意没有 implementation(project(":logkit"))——这个模块已发布(见
    // sanato.android.library.published),:logkit 没有,已发布模块依赖未发布
    // 模块会被根 build.gradle.kts 的 verifyModuleGraph 发布正确性检查拦下来。
    // `:app` 的 LogKit 调试面板通过 DebugDrawerContent.kt 暴露的 extraContent
    // 插槽挂进来,不靠这里的依赖边,见 debug-tools/README.md。
}
