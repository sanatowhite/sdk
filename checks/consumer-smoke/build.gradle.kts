// AGP 9 内置 Kotlin 默认带 2.2.10,但 Hilt 2.60.1 的 pom 要求 kotlin-stdlib
// >= 2.3.21——和根仓库 build.gradle.kts 顶部同一个理由,同一个覆盖方式
// (buildscript classpath,不是 plugins {})。这个工程是独立 build,没有
// version catalog,版本号直接写死在这里——它本来就该是"一个真实外部消费方
// 会写的样子",不共享仓库的 build-logic/catalog。
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.11")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
}
