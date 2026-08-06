plugins {
    id("com.android.library")
    // 刻意没有 org.jetbrains.kotlin.android、没有 maven-publish —— 仿
    // updatechecker/build.gradle.kts 的四条铁律,但本期 :logkit 不对外发布
    // (settings.gradle.kts 把它 gate 在 JITPACK != "true" 之外),所以连
    // maven-publish 插件都不需要套。
}

android {
    namespace = "io.sanato.logkit"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        // targetSdk 不设,理由同 updatechecker:AGP 9 已弃用 library 模块的
        // targetSdk。
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // Java 保持 11(不是其余模块用的 17):这是可能独立分发的 SDK 模块,消费方
    // 可能还在更旧的 AGP/JDK 上。理由与 updatechecker 完全一致。
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // 不写 kotlinOptions {} —— 内置 Kotlin 下已失效,jvmTarget 跟随上面的
    // compileOptions.targetCompatibility。

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    // 大多数测试(LogWriter/LogFileSet/…)刻意不用 Robolectric,直接在纯 JVM
    // 上跑,靠注入的 Clock/LogDirectory/resolveProcessTag/providePid 这些接缝
    // 摆脱对 android.* 的依赖——但内部诊断日志(LogWriter/LogKit 里的少量
    // `android.util.Log.e(...)` 调用)是刻意保留的直接触点,不值得为它们单独
    // 建一层抽象。没有这个开关,连这几条日志语句都会让测试因为
    // "not mocked" 抛异常;打开后它们安全地返回默认值,不影响任何断言。
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // 刻意为空 —— 零运行时依赖,只用 JDK + android.* 框架
    // (javax.crypto / java.security / java.util.zip / android.util.Log)。
    // 这比 updatechecker(仍需要 core-ktx + coroutines-android)更干净。
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
}

// ────────────────────────────────────────────────────────────────
// 轻量级公开 API 快照——"只许新增"这条铁律的机械检查点。
// 与 updatechecker/build.gradle.kts 的 apiDump/apiCheck 逐字节相同实现,
// 刻意重复而不是抽到 build-logic:抽出去既会破坏"不套 convention plugin"
// 这条规则,也会破坏未来 :logkit 走 ADR-0001 subtree 镜像发布的可能性
// (镜像出去的独立仓库里没有 build-logic)。AGP 版本升级导致下面这个
// intermediates 路径变化时,两个文件要一起改——见两边文件里的交叉引用注释。
// ────────────────────────────────────────────────────────────────
val apiGoldenFile = layout.projectDirectory.file("api/logkit.api").asFile

fun currentPublicApi(classesDir: File): String {
    val classNames =
        fileTree(classesDir) { include("**/*.class") }
            .files
            .map {
                it
                    .relativeTo(classesDir)
                    .path
                    .removeSuffix(".class")
                    .replace(File.separatorChar, '.')
            }
            // 跳过内部类/lambda 合成类,只看顶层类型的公开签名。
            .filterNot { it.contains('$') }
            .sorted()

    val output = StringBuilder()
    classNames.forEach { className ->
        val process =
            ProcessBuilder("javap", "-public", "-classpath", classesDir.absolutePath, className)
                .redirectErrorStream(true)
                .start()
        output.append(process.inputStream.bufferedReader().readText())
        process.waitFor()
        output.append("\n")
    }
    return output.toString()
}

tasks.register("apiDump") {
    dependsOn("compileReleaseKotlin")
    doLast {
        val classesDir =
            layout.buildDirectory
                .dir("intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes")
                .get()
                .asFile
        apiGoldenFile.parentFile.mkdirs()
        apiGoldenFile.writeText(currentPublicApi(classesDir))
        logger.lifecycle("Wrote ${apiGoldenFile.relativeTo(projectDir)}")
    }
}

tasks.register("apiCheck") {
    dependsOn("compileReleaseKotlin")
    doLast {
        val classesDir =
            layout.buildDirectory
                .dir("intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes")
                .get()
                .asFile
        val current = currentPublicApi(classesDir)
        val golden = if (apiGoldenFile.exists()) apiGoldenFile.readText() else ""
        if (current != golden) {
            throw GradleException(
                "Public API of :logkit changed. Review the diff, and if intentional, run " +
                    "./gradlew :logkit:apiDump to update ${apiGoldenFile.relativeTo(projectDir)}.",
            )
        }
    }
}
