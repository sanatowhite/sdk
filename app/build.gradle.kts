plugins {
    alias(libs.plugins.sanato.android.application)
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
}

android {
    namespace = "io.sanato.apptemplate"

    buildFeatures {
        compose = true
    }
}

aboutLibraries {
    // 离线模式:构建期不联网抓 license,CI 不会因为这一步变成不确定的网络依赖。
    offlineMode = true
}

// 唯一的开关来源是 gradle.properties 的 telemetryFirebaseEnabled——settings.gradle.kts
// 用它决定要不要 include(":telemetry-firebase"),这里必须读同一个值来决定要不要
// 依赖它 + apply 两个 Firebase 插件,两处不一致会导致"模块没被 include 但这里
// 硬编码依赖了它"这种直接失败的配置错误。
val telemetryFirebaseEnabled = providers.gradleProperty("telemetryFirebaseEnabled").getOrElse("false") == "true"

if (telemetryFirebaseEnabled) {
    // 条件 apply,不放进 plugins{} 块——google-services/crashlytics 插件的 classpath
    // 本身也是条件添加的(见根 build.gradle.kts 的 buildscript{}),两边必须一起开关。
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-ui"))
    implementation(project(":core-net"))
    implementation(project(":core-data"))
    implementation(project(":core-telemetry"))
    implementation(project(":updatechecker"))

    if (telemetryFirebaseEnabled) {
        implementation(project(":telemetry-firebase"))
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.aboutlibraries.compose.m3)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    debugImplementation(project(":debug-tools"))
}
