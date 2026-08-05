// AGP 9 内置 Kotlin：AGP 9.3.1 运行时自带 Kotlin Gradle Plugin 2.2.10，但本仓库要用
// 2.4.10（Hilt 2.60.1 的 pom 要求 kotlin-stdlib >= 2.3.21，AGP 自带的 2.2.10 不够）。
// 覆盖 KGP 版本的唯一受支持方式是这里的 buildscript classpath，不是 plugins {} ——
// 用 plugins {} 声明 org.jetbrains.kotlin.android 在内置 Kotlin 下会直接报
// "Cannot add extension with name 'kotlin', as there is an extension already registered
// with that name"，所以整个仓库都不再出现这个插件 id。
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")
    }
}

plugins {
    // 全部 apply false —— 真正的 apply 发生在 build-logic 的 convention plugin 里，
    // 或按需在各模块 build.gradle.kts 里。这里只是把插件版本集中声明一次。
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.aboutlibraries) apply false
    // 刻意不出现 org.jetbrains.kotlin.android。

    alias(libs.plugins.spotless)
}

// spotless 按文件 glob 工作，天然跨模块，放根上一次 apply 才能覆盖 build-logic/**
// 自己（放进某个 convention plugin 反而会漏掉 build-logic 的源码）。
//
// ⚠️ updatechecker/** 显式排除：它是对外发布的库，套用统一格式化规则会对
// 它的源码产生一次性大范围重排（trailing comma、多行签名换行等），这类
// 与本次改动目的无关的大 diff 正是 CLAUDE.md 六列自检表要挡住的"顺手改了别的"。
// 该模块要不要接入 ktlint 留给它自己独立决定，不跟随根仓库的格式化规则。
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/generated/**", "updatechecker/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("updatechecker/**")
        ktlint(libs.versions.ktlint.get())
    }
}

// Firebase 三个插件（google-services / crashlytics-gradle）走条件 buildscript
// classpath，不在这里的 plugins{} 块声明——即使 apply false 也会解析下载 plugin
// marker，走条件 classpath 才能做到"开关关着时零 Firebase 网络请求"。见
// `:telemetry-firebase` 模块（Phase 6）引入时的具体写法。
