plugins {
    alias(libs.plugins.sanato.android.library.compose)
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.sanato.android.library.published)
    // 刻意不 apply sanato.api.check：和 core-ui/feature-settings/feature-feedback
    // 同样的理由——UpdateDialog 是 @Composable 函数,Compose 编译器注入的参数会随
    // 版本漂移。API 稳定性改由 consumer-smoke(Phase 10)兜底。
}

android {
    namespace = "io.sanato.appkit.feature.update"

    // 见 feature-settings/build.gradle.kts 同一条注释。
    resourcePrefix = "appkit_"

    // 没有 src/testFixtures 源码,关掉避免发布出一个空的 -test-fixtures.aar。
    testFixtures.enable = false
}

dependencies {
    // UpdateInfo 出现在 UpdateUiState.Available/UpdateDialog 的公开签名里——
    // :feature-update 和 :updatechecker 现在同属一个发布集/同一坐标 group,
    // 用 project() 依赖,不需要额外的坐标替换(见 CLAUDE.md 里 :updatechecker
    // 加入统一发布集那条决策)。
    api(project(":updatechecker"))
    // AppScaffold 等 Compose 类型/BOM 对齐——理由见 core-ui/build.gradle.kts。
    api(project(":core-ui"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
