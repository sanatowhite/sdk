plugins {
    alias(libs.plugins.sanato.android.library.compose)
}

android {
    namespace = "io.sanato.apptemplate.debugtools"
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-net"))
    implementation(project(":core-telemetry"))
    // :debug-tools 不是 verifyModuleGraph 的 allowedProjectDeps 里的 key,
    // 不受"零内部依赖"约束——见根 build.gradle.kts 的注释。这里直接读
    // LogKit 的静态入口画 debug 面板(并发压测/滚动淘汰/崩溃触发/导出分享),
    // 不需要经过任何 DI。
    implementation(project(":logkit"))
}
