plugins {
    alias(libs.plugins.sanato.android.library.compose)
    alias(libs.plugins.sanato.android.roborazzi)
    alias(libs.plugins.sanato.android.library.published)
    // 刻意不 apply sanato.api.check：Compose 编译器注入的 Composer/$$changed
    // 参数会随编译器版本整体漂移，对 @Composable 公开函数做 javap 快照是噪音
    // 不是信号。这个模块的 API 稳定性改由 consumer-smoke 独立工程（Phase 10）
    // 兜底——它按名字调用每个公开入口，真正的签名删改会让它编译失败。
}

android {
    namespace = "io.sanato.appkit.core.ui"

    // 见 core-common/build.gradle.kts 的同一条注释：这个模块没有 src/testFixtures
    // 源码，关掉避免发布出一个空的 -test-fixtures.aar。
    testFixtures.enable = false
}

dependencies {
    // UiState<T> 是 StateContent(state: UiState<T>) 的参数类型。
    api(project(":core-common"))

    // Compose 是 api 的教科书案例：@Composable 注解本身（runtime）、Modifier/Dp
    // （ui）、PaddingValues（foundation-layout）、SnackbarHostState（material3）
    // 全部直接出现在 AppScaffold/StateContent/Spacing/AppTheme.spacing 的公开
    // 签名里。BOM 也必须 api：否则消费方拿到的 compose 各 artifact 版本不受
    // 这个 BOM 约束，会和它自己的 BOM 打架。
    //
    // sanato.android.library.compose 已经把这几个坐标声明成 implementation，
    // 这里再声明一次 api 是故意的：同一坐标出现在两处无害，效果以 api 为准。
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation.layout)
    api(libs.androidx.compose.material3)

    // 只在实现内部用，不出现在任何公开签名，保持 implementation：
    // material-icons-core（StateComponents 内部的 Icons.Outlined.Info/Warning）、
    // ui-tooling-preview（@Preview，消费方不需要）。
}
