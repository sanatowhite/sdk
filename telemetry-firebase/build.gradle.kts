// google-services / firebase-crashlytics 这两个 Gradle 插件不在这里 apply——
// 它们要求 applicationId 和 google-services.json 的 package_name 匹配,只能
// 挂在 :app(见 app/build.gradle.kts)。这个模块只提供 FirebaseTelemetryModule
// 的 Hilt 绑定 + Firebase SDK 依赖。
plugins {
    alias(libs.plugins.sanato.android.library)
    // 必须 apply——Phase 0 的 spike 证实库模块的 @Module 若不自己跑
    // hilt-compiler（KSP），@AggregatedDeps 元数据不会生成，:app 聚合不到，
    // 且不报任何编译错误（这个模块此前就是这么"死"的，见
    // docs/adr/spike-0000-hilt-library-module-aggregation.md）。
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.sanato.android.library.published)
    alias(libs.plugins.sanato.api.check)
}

android {
    namespace = "io.sanato.appkit.telemetry.firebase"

    // 见 core-common/build.gradle.kts 的同一条注释：这个模块没有 src/testFixtures
    // 源码，关掉避免发布出一个空的 -test-fixtures.aar。
    testFixtures.enable = false
}

dependencies {
    // FirebaseTelemetry : Telemetry（超接口）——必须 api。
    api(project(":core-telemetry"))

    implementation(libs.hilt.android)

    // FirebaseTelemetry(analytics: FirebaseAnalytics, crashlytics: FirebaseCrashlytics)
    // 两个构造参数类型都直接出现在公开签名里，BOM 同理需要 api。
    api(platform(libs.firebase.bom))
    api(libs.firebase.analytics)
    api(libs.firebase.crashlytics)
}
