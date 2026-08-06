package buildlogic

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.kotlin.dsl.of
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * 用 `ValueSource` 而不是直接 `project.exec { }`——Gradle 9 配置缓存默认开启,
 * `project.exec` 这类"配置期直接跑外部进程"的写法已经被移除,`ValueSource`
 * 是官方推荐的、配置缓存安全的替代方案。
 */
abstract class GitShaValueSource : ValueSource<String, GitShaValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val workingDir: DirectoryProperty
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val output = ByteArrayOutputStream()
        return runCatching {
            execOperations.exec {
                workingDir = parameters.workingDir.get().asFile
                commandLine("git", "rev-parse", "--short=10", "HEAD")
                standardOutput = output
                isIgnoreExitValue = true
            }
            output.toString(Charsets.UTF_8).trim()
        }.getOrNull()?.takeIf(String::isNotBlank) ?: "unknown"
    }
}

internal fun Project.gitShaProvider(): Provider<String> =
    providers.of(GitShaValueSource::class) {
        parameters.workingDir.set(rootProject.layout.projectDirectory)
    }
