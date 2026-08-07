plugins {
    alias(libs.plugins.sanato.android.library)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.core.init"

    // 没有 src/testFixtures 源码，关掉避免发布出一个空的 -test-fixtures.aar
    // （见 core-common/build.gradle.kts 的同一条注释）。
    testFixtures.enable = false
}

// 零内部模块依赖：AppInitializer/AppInitializers/FirstFrame 只用 javax.inject +
// Android framework 类型，不需要 core-common 的任何类型。
