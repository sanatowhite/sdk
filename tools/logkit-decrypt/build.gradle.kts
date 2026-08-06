// Phase 0 探针：验证 org.jetbrains.kotlin.jvm 在本仓库能否无版本号解析。
// 纯 JVM 模块、没有 AGP,理应不重演 ADR-0002 记录的
// "extension with name 'kotlin' already registered" 那个坑(那次冲突是
// AGP 的 Kotlin 扩展和显式 apply 的 org.jetbrains.kotlin.android 撞车;
// 这里连 AGP 都不存在)。KGP 版本来自根 build.gradle.kts 的 buildscript
// classpath override(2.4.10),plugins {} 不写 version 是唯一可行路径。
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass = "io.sanato.logkit.tools.MainKt"
}

// :logkit 的格式/加密代码单独放在 io.sanato.logkit.format 子包且不含任何
// android.* import,这里直接编译同一份源码——一份实现,零 project 依赖边。
sourceSets["main"].kotlin.srcDir("../../logkit/src/main/java/io/sanato/logkit/format")

dependencies {
    testImplementation("junit:junit:4.13.2")
}
