package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * 公开 API 快照：`javap -public` 输出 + 提交进库的 golden 文本 diff，机械检查
 * "对外发布的库只许新增、不许修改/删除公开 API" 这条铁律。
 *
 * 从 `:updatechecker` 手写的版本（该模块曾经在 build 文件末尾内联同一段逻辑）抽出来
 * 给所有发布模块复用，顺手修三个已发现的缺陷：
 *
 * 1. **嵌套类不再被整体误滤**。旧实现用 `!name.contains("$")` 排除，这会把所有
 *    sealed 子类（`AppError$Http`、`AppResult$Success` 等）连同真正的合成类一起滤掉——
 *    也就是说 sealed 子类的公开签名此前【完全不在】golden 文件里，删掉一个构造参数
 *    apiCheck 也不会报错。这里改成只排除已知的合成类名模式（Kotlin 编译器生成的
 *    lambda/WhenMappings/serializer 等），真正的嵌套类（含 sealed 子类、`Companion`）
 *    保留。
 * 2. **过滤 `access$...` 行**。Kotlin 为 inline 函数/内部类生成的合成访问器
 *    （`public static final ... access$getFoo$p(...)`）纯属实现细节，消费方用不到，
 *    但纯内部重构就可能改变它们的存在与否——留着会让 apiCheck 对不构成 API 变更的
 *    改动报假阳性。
 * 3. **真正的 Task 类**，而不是 `doLast { }` 里跑 `ProcessBuilder`——支持增量执行和
 *    配置缓存，CI 上不会每次都全量重跑 `javap`。
 */
@CacheableTask
abstract class ApiSnapshotTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classesDir: DirectoryProperty

    /** true = 校验模式（当前 API 与 golden 不一致就报错）；false = dump 模式（覆写 golden）。 */
    @get:Input
    abstract val checkOnly: Property<Boolean>

    @get:Optional
    @get:org.gradle.api.tasks.InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val existingGoldenFile: RegularFileProperty

    @get:OutputFile
    abstract val outputGoldenFile: RegularFileProperty

    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun run() {
        val current = currentPublicApi(classesDir.get().asFile)
        val outputFile = outputGoldenFile.get().asFile

        if (checkOnly.get()) {
            val golden =
                existingGoldenFile.orNull
                    ?.asFile
                    ?.takeIf(File::exists)
                    ?.readText() ?: ""
            if (current != golden) {
                throw GradleException(
                    "Public API of ${project.path} changed. Review the diff, and if intentional, run " +
                        "./gradlew ${project.path}:apiDump to update ${outputFile.name}.",
                )
            }
            // check 模式不修改 golden，但仍要把声明的 output 写成稳定内容，
            // 否则这个任务在增量场景下永远是 "no history" / 不可缓存。
            outputFile.parentFile.mkdirs()
            outputFile.writeText(golden)
        } else {
            outputFile.parentFile.mkdirs()
            outputFile.writeText(current)
            logger.lifecycle("Wrote ${outputFile.absolutePath}")
        }
    }

    private fun currentPublicApi(classesDir: File): String {
        val classNames =
            classesDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .map {
                    it
                        .relativeTo(classesDir)
                        .path
                        .removeSuffix(".class")
                        .replace(File.separatorChar, '.')
                }.filterNot(::isSyntheticClassName)
                .sorted()
                .toList()

        val output = StringBuilder()
        classNames.forEach { className ->
            val stdout = ByteArrayOutputStream()
            execOps.exec {
                commandLine("javap", "-public", "-classpath", classesDir.absolutePath, className)
                standardOutput = stdout
                errorOutput = stdout
                isIgnoreExitValue = true
            }
            stdout
                .toString(Charsets.UTF_8)
                .lineSequence()
                // 合成访问器（`access$getFoo$p` 等）是 Kotlin 编译器为 inline 函数/内部类
                // 生成的实现细节，消费方永远调不到，且纯内部重构就可能改变它们——不是
                // 需要被 apiCheck 盯住的公开 API 变化。
                .filterNot { it.contains("access$") }
                .forEach { output.appendLine(it) }
            output.appendLine()
        }
        return output.toString()
    }

    companion object {
        // 只匹配已知的 Kotlin/Java 编译器合成类名段（$ 之后的部分），真正的嵌套类
        // （sealed 子类、object、Companion 等）不受影响，继续出现在快照里。
        private val SYNTHETIC_NAME_SEGMENT =
            Regex(
                """^(\d+|serializer|WhenMappings|DefaultImpls|inlined\d*|sam\d*|ExternalSyntheticLambda\d*)${'$'}""",
            )

        internal fun isSyntheticClassName(className: String): Boolean {
            val segments = className.split('$')
            if (segments.size <= 1) return false // 顶层类，永远不是合成类
            return segments.drop(1).any { segment -> SYNTHETIC_NAME_SEGMENT.matches(segment) }
        }
    }
}
