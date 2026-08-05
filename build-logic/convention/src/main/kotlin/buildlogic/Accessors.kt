package buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * 类型安全的 `libs.xxx` 访问器（build script 里那种）在 convention plugin 代码里不可用——
 * 那是 Gradle 只在 build script 编译期生成的语法糖。这里手写等价的查找函数。
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal fun VersionCatalog.ver(alias: String) = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.plugin(alias: String) = findPlugin(alias).get()
