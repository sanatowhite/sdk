// google-services / firebase-crashlytics 这两个 Gradle 插件不在这里 apply——
// 它们要求 applicationId 和 google-services.json 的 package_name 匹配,只能
// 挂在 :app(见 app/build.gradle.kts 的条件 apply)。这个模块只提供
// FirebaseTelemetry 的实现代码 + Firebase SDK 依赖。
plugins {
    alias(libs.plugins.sanato.android.library)
}

android {
    namespace = "io.sanato.apptemplate.telemetry.firebase"
}

dependencies {
    implementation(project(":core-telemetry"))

    // 只要 hilt-android 提供 @Module/@Provides/@InstallIn/@IntoSet 这几个注解——
    // 不 apply Hilt Gradle 插件、不跑这个模块自己的 KSP。真正的 Hilt 聚合发生在
    // :app(@HiltAndroidApp 所在处),这个模块的 class 只要在 :app 的 classpath 上
    // 就会被聚合进同一个 Set<Telemetry> multibinding。
    implementation(libs.hilt.android)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
}
