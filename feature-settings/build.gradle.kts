plugins {
    alias(libs.plugins.sanato.android.library.compose)
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sanato.android.library.published)
    // 刻意不 apply sanato.api.check：和 core-ui 同样的理由——这个模块的公开
    // 签名里 SettingsScreen/AboutScreen/... 全是 @Composable 函数，Compose
    // 编译器注入的 Composer/$$changed 参数会随编译器版本整体漂移，javap
    // 快照对它们是噪音不是信号。API 稳定性改由 consumer-smoke（Phase 10）兜底。
}

android {
    namespace = "io.sanato.appkit.feature.settings"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    // 强制资源名前缀——防的是消费方无意中声明同名字符串静默覆盖库的文案
    // （比如消费方自己声明 "settings_title" 撞车的概率不低,"appkit_settings_title"
    // 基本为零)。想改文案/加语言,靠标准 Android 资源覆盖机制:在消费方自己的
    // values*/strings.xml 里声明同名 key 即可,库侧不做任何特殊处理。
    resourcePrefix = "appkit_"

    // 没有 src/testFixtures 源码，关掉避免发布出一个空的 -test-fixtures.aar。
    testFixtures.enable = false
}

dependencies {
    // UserSettings/ThemeMode/UserSettingsRepository、AppBuildInfo 都直接出现在
    // 公开签名里。
    api(project(":core-common"))
    api(project(":core-ui"))
    api(project(":core-data"))
    // NavGraphBuilder/NavController 出现在 settingsGraph() 的公开签名里。
    api(libs.androidx.navigation.compose)

    // 默认 Hilt 装配——想换实现就 exclude 这两个模块，见各自 README。
    implementation(project(":core-data-hilt"))
    implementation(project(":core-common-hilt"))

    // LocaleManager 内部用 AppCompatDelegate，但它自己的公开签名只有 String?，
    // 不泄漏 appcompat 类型，implementation 足够。
    implementation(libs.androidx.appcompat)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(testFixtures(project(":core-data")))
}
