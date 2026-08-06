plugins {
    alias(libs.plugins.sanato.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sanato.android.library.published)
    // 刻意不 apply sanato.api.check：和 core-ui/feature-settings 同样的理由——
    // LicensesScreen 是 @Composable 函数,Compose 编译器注入的 Composer/$$changed
    // 参数会随编译器版本整体漂移,javap 快照对它是噪音不是信号。API 稳定性改由
    // consumer-smoke(Phase 10)兜底。
}

android {
    namespace = "io.sanato.appkit.feature.licenses"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    // 见 feature-settings/build.gradle.kts 同一条注释:防的是消费方无意中声明
    // 同名字符串静默覆盖库的文案。
    resourcePrefix = "appkit_"

    // 没有 src/testFixtures 源码,关掉避免发布出一个空的 -test-fixtures.aar。
    testFixtures.enable = false
}

dependencies {
    // AppScaffold 的公开签名里到处是 Modifier/PaddingValues 等 Compose 类型,
    // core-ui 已经把这些声明成 api,这里不用重复声明。
    api(project(":core-ui"))

    // NavGraphBuilder/NavController 出现在 licensesGraph() 的公开签名里。
    api(libs.androidx.navigation.compose)

    // Libs/produceLibraries/LibrariesContainer 只在 LicensesScreen 函数体内部用,
    // 不出现在任何公开签名(公开签名只有 @RawRes Int)——不是 inline 函数,函数体
    // 不会被复制进消费方字节码,implementation 足够。消费方要不要真的把
    // AboutLibraries 运行时库摆上自己的 classpath 是它自己的选择,不该被这个模块
    // 的公开 API 强制传播。
    implementation(libs.aboutlibraries.compose.m3)
}
