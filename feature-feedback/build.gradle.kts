plugins {
    alias(libs.plugins.sanato.android.library.compose)
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sanato.android.library.published)
    // 刻意不 apply sanato.api.check：和 core-ui/feature-settings/feature-licenses
    // 同样的理由——FeedbackScreen/FeedbackRoute 是 @Composable 函数,Compose 编译器
    // 注入的参数会随版本漂移。API 稳定性改由 consumer-smoke(Phase 10)兜底。
}

android {
    namespace = "io.sanato.appkit.feature.feedback"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    // 见 feature-settings/build.gradle.kts 同一条注释。
    resourcePrefix = "appkit_"

    // 没有 src/testFixtures 源码,关掉避免发布出一个空的 -test-fixtures.aar。
    testFixtures.enable = false
}

dependencies {
    // AppBuildInfo 出现在 FeedbackViewModel 的公开构造函数里(邮件标题里的版本号
    // /gitSha)。
    api(project(":core-common"))
    // RingLogBuffer 出现在 FeedbackViewModel 的公开构造函数里(反馈邮件附带日志)。
    api(project(":core-telemetry"))
    // AppScaffold 等 Compose 类型出现在渲染里,BOM 也要保持和 core-ui 对齐——理由
    // 见 core-ui/build.gradle.kts。
    api(project(":core-ui"))
    // FeedbackRoute()/feedbackGraph() 公开签名里的 NavGraphBuilder/NavController。
    api(libs.androidx.navigation.compose)

    // 只提供 AppBuildInfo 的 Hilt 默认绑定,不额外引入公开签名会用到的类型——
    // 想换绑定来源就 exclude 这个模块,见 core-common-hilt/README.md。
    implementation(project(":core-common-hilt"))
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
