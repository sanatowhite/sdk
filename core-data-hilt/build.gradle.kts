plugins {
    alias(libs.plugins.sanato.android.library)
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.core.data.hilt"

    // 没有 src/testFixtures 源码，关掉避免发布出一个空的 -test-fixtures.aar。
    testFixtures.enable = false
}

dependencies {
    // DataStoreUserSettingsRepository / UserSettingsRepository 出现在绑定签名里。
    api(project(":core-data"))
}
