plugins {
    alias(libs.plugins.sanato.android.library)
    // 必须 apply——spike-0000 证实库模块的 @Module 若不自己跑 hilt-compiler(KSP),
    // @AggregatedDeps 元数据不会生成,:app 聚合不到,且不报任何编译错误。
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.download.hilt"
    testFixtures.enable = false
}

dependencies {
    // Downloader 直接出现在 @Provides 的返回类型里。
    api(project(":downloadkit"))
    // 组装 Downloader 时要用 HttpClientFactory.okHttpClient() / NetworkMetricsSink，
    // 都只在方法体内部使用，不出现在本模块自己的公开签名里。
    implementation(project(":core-net"))
    // isDebuggableBuild() 组装带日志的 OkHttpClient 时用。
    implementation(project(":core-common"))

    implementation(libs.hilt.android)

    testImplementation(libs.hilt.android.testing)
}
