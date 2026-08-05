// AGP 9 内置 Kotlin：AGP 9.3.1 运行时自带 Kotlin Gradle Plugin 2.2.10，但本仓库要用
// 2.4.10（compileSdk 37 相关生态、以后接 Hilt/KSP 都需要更高版本）。覆盖 KGP 版本的
// 唯一受支持方式是这里的 buildscript classpath，不是 plugins {} —— 用 plugins {}
// 声明 org.jetbrains.kotlin.android 在内置 Kotlin 下会直接报
// "Cannot add extension with name 'kotlin', as there is an extension already registered
// with that name"，所以整个仓库都不再出现这个插件 id。
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.library") version "9.3.1" apply false
}
