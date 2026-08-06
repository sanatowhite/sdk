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

        // 条件 classpath,不用 plugins { apply false } —— 后者即使 apply false 也会
        // 解析下载 plugin marker,走这里才能做到"开关关着时零 Firebase 网络请求"。
        // （`buildscript {}` 和 `pluginManagement {}` 一样是提前抽取编译的独立片段,
        // 这里不引用外部顶层 val,直接内联判断表达式,避免重蹈 settings.gradle.kts
        // 那次"Unresolved reference"的覆辙。）
        if (providers.gradleProperty("telemetryFirebaseEnabled").getOrElse("false") == "true") {
            classpath("com.google.gms:google-services:${libs.versions.googleServices.get()}")
            classpath("com.google.firebase:firebase-crashlytics-gradle:${libs.versions.crashlyticsPlugin.get()}")
        }
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
    alias(libs.plugins.aboutlibraries) apply false
    // 刻意不出现 org.jetbrains.kotlin.android。

    alias(libs.plugins.spotless)
    // kover 在根项目真的 apply(不是 apply false)——聚合报告需要它。
    alias(libs.plugins.kover)
}

// Kover 聚合报告——quality-report(非阻塞)CI job 用。`:updatechecker` 故意不
// 纳入(它的覆盖率不该被这套模板的质量门禁牵动,保持独立评估的原则和它不套用
// build-logic convention plugin 是同一个道理)。`:logkit` 同理排除,且原因完全
// 相同:聚合机制本身要求给模块 apply kover 插件,这直接违反"除 com.android.library
// 外不套任何插件"这条铁律——加了就不再是零插件的独立模块。`:logkit-decrypt`
// 是纯 JVM 模块,不参与 Android 模块的聚合体系,同样不在这个列表里。
// 每个参与聚合的子项目也要 apply 这个插件本身,根项目的 `kover(project(...))`
// 依赖才能找到匹配的 "kover" variant——这是 Kover 多模块聚合报告的前提。
val koverAggregatedModules =
    listOf(":core-common", ":core-ui", ":core-net", ":core-data", ":core-telemetry", ":app")

// ⚠️ 修复一个先于本次改动就存在的 bug:这两处 project(path) 在配置期就 eager
// 求值,但 JITPACK=true 时 settings.gradle.kts 根本不 include 这些模块——
// `JITPACK=true ./gradlew :updatechecker:publishToMavenLocal ...`(CLAUDE.md
// 里记录的标准 SDK-only 构建命令)因此在配置阶段直接报
// "Project with path ':core-common' could not be found",连 :updatechecker
// 都跑不起来。同一份文件后面 verifyModuleGraph 用的是 doLast{} 里的惰性
// project(path),从未触发这个问题——只有这两行是例外。
if (System.getenv("JITPACK") != "true") {
    koverAggregatedModules.forEach { path -> project(path).apply(plugin = "org.jetbrains.kotlinx.kover") }

    dependencies {
        koverAggregatedModules.forEach { path -> kover(project(path)) }
    }
}

// 模块依赖方向的机械检查——只检查 project() 依赖,不解析构建脚本文本。
// 用允许表而不是禁止表:没在表里的模块(:app/:debug-tools/:telemetry-firebase/
// :benchmark/:baselineprofile)不受这条规则约束,可以自由依赖任何东西。
//
// 三条最重要的规则在这里体现:core-ui/core-net/core-telemetry 互相之间不能
// 依赖(粘合只发生在 :app);core-data 可以依赖 core-net(为了共享 AppResult/
// AppError 之类的类型);:updatechecker 内部依赖数必须是 0(硬约束,发布产物
// 的 POM 不能带上消费方拿不到的坐标)。
tasks.register("verifyModuleGraph") {
    doLast {
        val allowedProjectDeps =
            mapOf(
                ":core-common" to emptySet<String>(),
                ":core-ui" to setOf(":core-common"),
                ":core-net" to setOf(":core-common"),
                ":core-data" to setOf(":core-common", ":core-net"),
                ":core-telemetry" to setOf(":core-common"),
                ":updatechecker" to emptySet<String>(),
                // 和 :updatechecker 同一条铁律,但理由不同::logkit 没有发布链路
                // 要保护(本期不对外发布),但它是 ADR-0008 里 subtree 镜像的候选,
                // 镜像出去的单模块仓库里根本不存在 :core-common ——依赖数必须
                // 恒为 0,不是"现在恰好没依赖"。
                ":logkit" to emptySet<String>(),
                ":logkit-decrypt" to emptySet<String>(),
            )

        val violations = mutableListOf<String>()
        allowedProjectDeps.forEach { (modulePath, allowedDeps) ->
            val moduleProject = project(modulePath)
            val configNames = listOf("implementation", "api")
            val actualDeps =
                configNames
                    .mapNotNull { moduleProject.configurations.findByName(it) }
                    .flatMap { it.dependencies }
                    .filterIsInstance<ProjectDependency>()
                    .map { it.path }
                    .toSet()
            val forbidden = actualDeps - allowedDeps
            if (forbidden.isNotEmpty()) {
                violations += "$modulePath depends on $forbidden, only $allowedDeps allowed"
            }
        }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error("[verifyModuleGraph] $it") }
            throw GradleException("Module dependency graph violations found (see log above).")
        }
        logger.lifecycle("[verifyModuleGraph] OK — all module dependencies respect the allowed graph.")
    }
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
