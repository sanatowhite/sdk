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
    alias(libs.plugins.aboutlibraries) apply false
    // Firebase 是 :app 的默认遥测后端（见 telemetry-firebase/README.md）——
    // 不再是条件 classpath，和其余插件一样集中声明版本、apply false，
    // 真正 apply 只在 app/build.gradle.kts。
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
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
//
// ⚠️ `findProject` 而不是 `project()`：JITPACK=true 时 `:app` 不在 settings 的
// include 列表里，`project(":app")` 会在【配置期】直接抛异常——这比
// `verifyModuleGraph` 的 `doLast` 硬引用更早、更硬，会让整条 JitPack 发布链在
// 跑到任何任务之前就死在配置阶段。
val koverAggregatedModules =
    listOf(":core-common", ":core-init", ":core-ui", ":core-net", ":core-data", ":core-telemetry", ":app")

val koverProjects = koverAggregatedModules.mapNotNull { findProject(it) }
koverProjects.forEach { it.apply(plugin = "org.jetbrains.kotlinx.kover") }

dependencies {
    koverProjects.forEach { kover(project(it.path)) }
}

// ── SDK 发布模块清单：本文件是权威，settings.gradle.kts 的 include 列表跟随，
// 漂移由下方 verifySdkModuleList 任务机械检查。Phase 9 加入 feature-feedback/
// feature-licenses/feature-update 后到齐 18 个;sdk-bom(Phase 10 加入)没有
// apiCheck，需要在 apiCheckAll 里单独排除。
val sdkModules =
    listOf(
        ":updatechecker",
        ":core-common",
        ":core-common-hilt",
        ":core-init",
        ":core-init-hilt",
        ":core-ui",
        ":core-net",
        ":core-data",
        ":core-data-hilt",
        ":core-telemetry",
        ":core-telemetry-hilt",
        ":net-telemetry-hilt",
        ":debug-tools",
        ":telemetry-firebase",
        ":feature-settings",
        ":feature-feedback",
        ":feature-licenses",
        ":feature-update",
        ":sdk-bom",
    )

// JitPack 的 install 命令只调这一个任务：显式枚举 > 不带路径的 `publishToMavenLocal`
// 匹配所有子项目——后者在有人不小心给 :app 加上 maven-publish 时会静默多发一个
// artifact，而 JitPack 的坐标规则是按 m2 里最终 artifact 集合算的。
tasks.register("publishSdkToMavenLocal") {
    group = "publishing"
    description = "Publish every SDK module to mavenLocal (mirrors jitpack.yml's install step)."
    dependsOn(sdkModules.map { "$it:publishToMavenLocal" })
}

// apiCheck 被排除的模块清单——两类原因:
// 1) Compose 编译器版本漂移噪音(理由同各模块 build.gradle.kts 里的注释),API
//    稳定性改由 consumer-smoke 兜底。
// 2) :sdk-bom 是纯 java-platform,没有 Kotlin 源码,压根不存在 apiCheck 任务。
val apiCheckExcludedModules =
    setOf(
        ":core-ui",
        ":feature-settings",
        ":feature-feedback",
        ":feature-licenses",
        ":feature-update",
        ":sdk-bom",
    )

tasks.register("apiCheckAll") {
    group = "verification"
    description =
        "Run apiCheck across every SDK module that has one (Compose-heavy modules excluded, see apiCheckExcludedModules)."
    dependsOn(sdkModules.filterNot { it in apiCheckExcludedModules }.map { "$it:apiCheck" })
}

// 防漂移：sdkModules 这张手写清单 vs 实际 apply 了 maven-publish 的模块。
// 用 subprojects（而不是 project(path)）遍历，JITPACK 模式下天然只看到被
// include 的那些，不会因为模块缺席而配置期崩溃。
tasks.register("verifySdkModuleList") {
    group = "verification"
    doLast {
        val actual =
            subprojects
                .filter { it.pluginManager.hasPlugin("maven-publish") }
                .map { it.path }
                .toSortedSet()
        val declared = sdkModules.toSortedSet()
        if (actual != declared) {
            throw GradleException(
                "SDK module list drift: declared=$declared actual=$actual. " +
                    "Update sdkModules in build.gradle.kts AND the include list in settings.gradle.kts.",
            )
        }
        logger.lifecycle("[verifySdkModuleList] OK — declared list matches modules that apply maven-publish.")
    }
}

// 防漂移：sdk-bom/build.gradle.kts 里手写的约束模块清单 vs 这里的 sdkModules
// (去掉 sdk-bom 自己)。两处必须同步更新,和 verifySdkModuleList 是同一个套路。
tasks.register("verifySdkBomConstraints") {
    group = "verification"
    doLast {
        val bom = findProject(":sdk-bom")
        if (bom == null) {
            logger.lifecycle("[verifySdkBomConstraints] skipped — :sdk-bom not included in this build.")
            return@doLast
        }
        val declared =
            bom.configurations
                .getByName("api")
                .dependencyConstraints
                .mapNotNull { it.name }
                .toSortedSet()
        val expected = sdkModules.filterNot { it == ":sdk-bom" }.map { it.removePrefix(":") }.toSortedSet()
        if (declared != expected) {
            throw GradleException(
                "sdk-bom constraints drift: declared=$declared expected=$expected. " +
                    "Update the module list in sdk-bom/build.gradle.kts AND sdkModules in this file.",
            )
        }
        logger.lifecycle("[verifySdkBomConstraints] OK — sdk-bom constraints match sdkModules.")
    }
}

// 模块依赖方向的机械检查——只检查 project() 依赖,不解析构建脚本文本。
// 用允许表而不是禁止表:没在表里的模块(:app/:benchmark/:baselineprofile)
// 不受这条规则约束,可以自由依赖任何东西。
//
// Tier 1 能力层(core-common/core-init/core-ui/core-net/core-data/core-telemetry)
// 互相之间零粘合(core-ui/core-net/core-telemetry 互不依赖),粘合只发生在
// Tier 3 装配层(*-hilt 模块)——net-telemetry-hilt 是唯一跨 core-net/
// core-telemetry 的桥,这条规则现在从"core-ui/core-net/core-telemetry 不能
// 互相依赖"升级为分层规则。:updatechecker 内部依赖数必须是 0(硬约束,发布
// 产物的 POM 不能带上消费方拿不到的坐标)。
//
// 新增的硬约束(第二段 forEach 里的 leaks 检查):可发布模块不得依赖不可发布
// 模块——这不只是方向问题,是发布正确性问题:一旦违反,发布出去的 POM 里会
// 出现一条消费方永远解析不到的坐标。
tasks.register("verifyModuleGraph") {
    doLast {
        // publishable：会被发布成 Maven 坐标的模块，必须和 sdkModules 保持同步。
        val publishable = sdkModules.toSet()

        val allowedProjectDeps =
            mapOf(
                // ── Tier 1：能力层，零 Hilt，彼此零粘合 ──
                ":core-common" to emptySet<String>(),
                ":core-init" to emptySet<String>(),
                ":core-ui" to setOf(":core-common"),
                ":core-net" to setOf(":core-common"),
                ":core-data" to setOf(":core-common"),
                ":core-telemetry" to setOf(":core-common", ":core-init"),
                // ── Tier 3：装配层，唯一允许跨 Tier-1 边界粘合的地方 ──
                ":core-common-hilt" to setOf(":core-common"),
                ":core-init-hilt" to setOf(":core-init"),
                ":core-data-hilt" to setOf(":core-data"),
                ":core-telemetry-hilt" to setOf(":core-telemetry", ":core-init-hilt", ":core-common"),
                ":net-telemetry-hilt" to setOf(":core-net", ":core-telemetry"),
                ":telemetry-firebase" to setOf(":core-telemetry"),
                // ── Tier 2：标准页面 ──
                ":feature-settings" to
                    setOf(":core-common", ":core-ui", ":core-data", ":core-data-hilt", ":core-common-hilt"),
                ":feature-feedback" to
                    setOf(":core-common", ":core-telemetry", ":core-ui", ":core-common-hilt"),
                ":feature-licenses" to setOf(":core-ui"),
                ":feature-update" to setOf(":updatechecker", ":core-ui"),
                // ── 其余 ──
                ":debug-tools" to setOf(":core-telemetry"),
                ":updatechecker" to emptySet<String>(),
                // 和 :updatechecker 同一条铁律,但理由不同::logkit 还不在 sdkModules
                // 发布清单里(见 CLAUDE.md ":logkit 五条铁律" #1),依赖数必须恒为
                // 0,不是"现在恰好没依赖"——见 docs/adr/0010。
                ":logkit" to emptySet<String>(),
                ":logkit-decrypt" to emptySet<String>(),
            )

        val violations = mutableListOf<String>()
        val checked = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        allowedProjectDeps.forEach { (modulePath, allowedDeps) ->
            val moduleProject = findProject(modulePath)
            if (moduleProject == null) {
                // 可发布模块必须在【任何】模式下都存在——它缺席就是 settings 的
                // include 列表和这张表漂移了,是真错误,不是"这次没 include"。
                if (modulePath in publishable) {
                    violations += "$modulePath is publishable but not included in settings.gradle.kts"
                } else {
                    skipped += modulePath
                }
                return@forEach
            }
            checked += modulePath

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

            if (modulePath in publishable) {
                val leaks = actualDeps - publishable
                if (leaks.isNotEmpty()) {
                    violations += "$modulePath is publishable but depends on non-publishable $leaks"
                }
            }
        }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error("[verifyModuleGraph] $it") }
            throw GradleException("Module dependency graph violations found (see log above).")
        }
        // 明确打印 skipped —— 否则"JITPACK 模式下全被跳过 = 静默通过"会是下一个坑。
        logger.lifecycle("[verifyModuleGraph] OK — checked ${checked.size} modules, skipped $skipped.")
    }
}

// spotless 按文件 glob 工作，天然跨模块，放根上一次 apply 才能覆盖 build-logic/**
// 自己（放进某个 convention plugin 反而会漏掉 build-logic 的源码）。
//
// ⚠️ updatechecker/** 显式排除：它是对外发布的库，套用统一格式化规则会对
// 它的源码产生一次性大范围重排（trailing comma、多行签名换行等），这类
// 与本次改动目的无关的大 diff 正是 CLAUDE.md 六列自检表要挡住的"顺手改了别的"。
// 该模块要不要接入 ktlint 留给它自己独立决定，不跟随根仓库的格式化规则。
//
// ⚠️ checks/consumer-smoke/** 同样排除，理由不同：那是一个完全独立的 Gradle
// build（自己的 settings.gradle.kts，不被这里 include），spotless 的 target
// glob 是纯文件系统扫描，不认 Gradle 的 build 边界，不排除的话根 build 的
// spotlessCheck 会去检查一个不属于它的项目，语义上就不对——那个工程要不要用
// ktlint、用什么规则，是它自己的事。
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/generated/**", "updatechecker/**", "checks/consumer-smoke/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("updatechecker/**", "checks/consumer-smoke/**")
        ktlint(libs.versions.ktlint.get())
    }
}
