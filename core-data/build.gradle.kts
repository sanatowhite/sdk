plugins {
    alias(libs.plugins.sanato.android.library)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.core.data"
}

dependencies {
    api(project(":core-common"))
    // ⚠️ implementation(project(":core-net")) 删掉——源码零引用(已确认)。
    // 它此前的唯一作用是让 core-data 的 POM 无谓拖上 okhttp/retrofit/
    // kotlinx-serialization 三条传递依赖链。README 和旧版 verifyModuleGraph
    // 白名单都曾为这条死依赖背书，源码事实优先于文档。

    // DataStore<Preferences> 既是 DataStoreUserSettingsRepository 的构造参数
    // 类型，也是 public 扩展属性 Context.userSettingsDataStore 的类型。
    api(libs.androidx.datastore.preferences)
    // Flow<UserSettings> 是 UserSettingsRepository.settings 的类型。
    api(libs.kotlinx.coroutines.android)

    // testFixtures 不自动继承 main 的 implementation 依赖,FakeUserSettingsRepository
    // 用到 MutableStateFlow/update 需要显式声明。
    testFixturesImplementation(libs.kotlinx.coroutines.android)
}
