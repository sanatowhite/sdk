import buildlogic.ApiSnapshotTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * 复用的公开 API 快照插件：给 apply 它的模块注册 `apiDump` / `apiCheck` 两个任务，
 * 机械检查"对外发布的库只许新增、不许修改/删除公开 API"这条铁律。
 *
 * 从 `:updatechecker` 手写的一次性实现（原先内联在它的 build 文件末尾）抽出来复用，
 * 具体修复内容见 [ApiSnapshotTask] 的 KDoc。
 *
 * 只对真正想要这项检查的模块 apply——例如 `:core-ui` 故意不 apply 它：Compose
 * 编译器注入的 `Composer` / `$$changed` 参数会随编译器版本整体漂移，那是噪音不是
 * 信号；`:core-ui` 的 API 稳定性改由 consumer-smoke 独立工程（Phase 10）兜底。
 */
class SanatoApiCheckConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            // AGP 9 内置 kotlinc 的固定输出路径——:updatechecker 现有实现已经验证过
            // 这条路径在有/无 convention plugin 两种情况下都成立。
            val classesDirProvider =
                layout.buildDirectory.dir(
                    "intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes",
                )
            val goldenFile = layout.projectDirectory.file("api/${project.name}.api")

            val apiDumpTask =
                tasks.register<ApiSnapshotTask>("apiDump") {
                    group = "verification"
                    description = "Regenerate the public API golden snapshot for ${project.path}."
                    dependsOn("compileReleaseKotlin")
                    classesDir.set(classesDirProvider)
                    checkOnly.set(false)
                    outputGoldenFile.set(goldenFile)
                }

            tasks.register<ApiSnapshotTask>("apiCheck") {
                group = "verification"
                description = "Check the public API of ${project.path} against its golden snapshot."
                dependsOn("compileReleaseKotlin")
                classesDir.set(classesDirProvider)
                checkOnly.set(true)
                existingGoldenFile.set(goldenFile)
                // check 模式的声明输出是 build 目录下的一个 stamp 文件，不是直接写
                // golden——避免这个任务被增量判定为"永远需要重跑"，也避免它意外
                // 覆盖检查进版本库的 golden 文件（那是 apiDump 的职责）。
                outputGoldenFile.set(layout.buildDirectory.file("apiCheck/${project.name}.api"))

                // ⚠️ apiCheck 读的 existingGoldenFile 和 apiDump 写的 outputGoldenFile
                // 是同一个物理文件——两个任务若在同一次调用里都被请求（比如
                // `./gradlew apiDump apiCheckAll`），Gradle 的"隐式依赖"校验会报错：
                // 一个任务读另一个任务声明的输出，却没有显式顺序关系，结果可能因
                // 执行顺序不同而不同。apiCheck 的语义是"和已提交进版本库的 golden
                // 比对"，不依赖 apiDump 在同一次构建里跑没跑，所以用 mustRunAfter
                // （只声明顺序，不建立真正的执行依赖）而不是 dependsOn 满足这条校验。
                mustRunAfter(apiDumpTask)
            }
            Unit
        }
}
