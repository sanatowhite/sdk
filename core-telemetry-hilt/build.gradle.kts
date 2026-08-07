plugins {
    alias(libs.plugins.sanato.android.library)
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.core.telemetry.hilt"

    // 没有 src/testFixtures 源码，关掉避免发布出一个空的 -test-fixtures.aar。
    testFixtures.enable = false
}

dependencies {
    // Telemetry/RingLogBuffer/AppForegroundState/MemorySampler 等直接出现在
    // provider 签名里。
    api(project(":core-telemetry"))
    // TelemetryApplication 继承 HiltInitializingApplication；
    // TelemetryBackendsModule 的 @Binds @IntoSet 绑定用到 core-init 的
    // @Eager/@Deferred qualifier。
    api(project(":core-init-hilt"))
    // Context.isDebuggableBuild() 只在 provideLogcatOrNoOpTelemetry() 函数体
    // 内部调用，不出现在任何公开签名里，implementation 足够。
    implementation(project(":core-common"))
}
