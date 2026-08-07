plugins {
    alias(libs.plugins.sanato.android.library)
    // 必须 apply——Phase 0 的 spike 证实库模块的 @Module 若不自己跑
    // hilt-compiler（KSP），@AggregatedDeps 元数据不会生成，:app 聚合不到，
    // 且不报任何编译错误。
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.core.common.hilt"

    // 没有 src/testFixtures 源码，关掉避免发布出一个空的 -test-fixtures.aar。
    testFixtures.enable = false
}

dependencies {
    // AppBuildInfo 是 provideAppBuildInfo() 的返回类型。
    api(project(":core-common"))
}
