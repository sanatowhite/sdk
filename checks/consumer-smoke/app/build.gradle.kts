import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 默认从仓库根目录的 gradle/version.properties 读 sdkVersion——本地跑
// `publishSdkToMavenLocal`(不传 -Pversion)时用的就是这个值,两边自动对上。
// CI probe 场景需要一个临时版本号时,`-PsmokeVersion=X` 覆盖。
val smokeVersion: String =
    providers.gradleProperty("smokeVersion").orNull
        ?: Properties()
            .apply { rootDir.resolve("../../gradle/version.properties").inputStream().use(::load) }
            .getProperty("sdkVersion")
        ?: error("gradle/version.properties is missing sdkVersion=")

android {
    namespace = "io.sanato.appkit.smoke"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.sanato.appkit.smoke"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // sdk-bom 之后下面每个坐标都不用写版本号——这本身就是对 sdk-bom 的一次
    // 真实验证(不是靠约定"版本号应该一样",是靠 Gradle 真的解析出同一个版本)。
    implementation(platform("com.github.sanatowhite.sdk:sdk-bom:$smokeVersion"))

    implementation("com.github.sanatowhite.sdk:updatechecker")
    implementation("com.github.sanatowhite.sdk:core-common")
    implementation("com.github.sanatowhite.sdk:core-common-hilt")
    implementation("com.github.sanatowhite.sdk:core-init")
    implementation("com.github.sanatowhite.sdk:core-init-hilt")
    implementation("com.github.sanatowhite.sdk:core-ui")
    implementation("com.github.sanatowhite.sdk:core-net")
    implementation("com.github.sanatowhite.sdk:core-data")
    implementation("com.github.sanatowhite.sdk:core-data-hilt")
    implementation("com.github.sanatowhite.sdk:core-telemetry")
    implementation("com.github.sanatowhite.sdk:core-telemetry-hilt")
    implementation("com.github.sanatowhite.sdk:net-telemetry-hilt")
    implementation("com.github.sanatowhite.sdk:debug-tools")
    implementation("com.github.sanatowhite.sdk:telemetry-firebase")
    implementation("com.github.sanatowhite.sdk:feature-settings")
    implementation("com.github.sanatowhite.sdk:feature-feedback")
    implementation("com.github.sanatowhite.sdk:feature-licenses")
    implementation("com.github.sanatowhite.sdk:feature-update")

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // 这个模块自己 apply 了 com.google.dagger.hilt.android 插件(为了
    // @HiltAndroidApp/@AndroidEntryPoint)——Hilt Gradle 插件的
    // verifyDependencies 检查只看直接 apply 插件的那个模块自己声明了什么,
    // 不看 -hilt 伴生模块内部各自声明的 hilt-android(那些对它们自己有效,
    // 传递不到这里的插件校验)。任何真实外部消费方的 app 模块都要这样写,
    // 和仓库里 :app 自己走 sanato.android.hilt convention plugin 时内部
    // 做的事完全一样(见 SanatoAndroidHiltConventionPlugin.kt)。
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
}
