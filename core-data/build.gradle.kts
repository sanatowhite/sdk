plugins {
    alias(libs.plugins.sanato.android.library)
}

android {
    namespace = "io.sanato.apptemplate.core.data"
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-net"))

    implementation(libs.androidx.datastore.preferences)

    // testFixtures 不自动继承 main 的 implementation 依赖,FakeUserSettingsRepository
    // 用到 MutableStateFlow/update 需要显式声明。
    testFixturesImplementation(libs.kotlinx.coroutines.android)
}
