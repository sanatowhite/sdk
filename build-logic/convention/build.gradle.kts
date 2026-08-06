plugins {
    `kotlin-dsl`
}

group = "io.sanato.buildlogic"

// ⚠️ 这是 build-logic 自身的 Kotlin 版本，跟项目模块用的 Kotlin 2.4.10 是两回事——
// kotlin-dsl 插件用的是 Gradle 内嵌的 Kotlin（Gradle 9.6.1 内嵌 2.3.21，
// 见 `./gradlew --version` 的输出）。convention plugin 源码里不能用内嵌版本
// 不支持的 Kotlin 语法，这是 build-logic 最容易踩的坑。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // compileOnly：这些插件在【消费方】构建的 classpath 上已经存在（root buildscript /
    // plugins 块提供），build-logic 只需要类型信息来编译 convention plugin 代码，
    // 不该把它们打进运行时依赖。
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.roborazzi.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "sanato.android.library"
            implementationClass = "SanatoAndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "sanato.android.library.compose"
            implementationClass = "SanatoAndroidLibraryComposeConventionPlugin"
        }
        register("androidApplication") {
            id = "sanato.android.application"
            implementationClass = "SanatoAndroidApplicationConventionPlugin"
        }
        register("androidHilt") {
            id = "sanato.android.hilt"
            implementationClass = "SanatoAndroidHiltConventionPlugin"
        }
        register("androidRoborazzi") {
            id = "sanato.android.roborazzi"
            implementationClass = "SanatoAndroidRoborazziConventionPlugin"
        }
        register("quality") {
            id = "sanato.quality"
            implementationClass = "SanatoQualityConventionPlugin"
        }
        register("publishedLibrary") {
            id = "sanato.android.library.published"
            implementationClass = "SanatoPublishedLibraryConventionPlugin"
        }
        register("apiCheck") {
            id = "sanato.api.check"
            implementationClass = "SanatoApiCheckConventionPlugin"
        }
    }
}
