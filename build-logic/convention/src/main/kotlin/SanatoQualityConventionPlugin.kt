import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * detekt 是 advisory，不是 required check——1.23.x 内嵌的是旧版 Kotlin 编译器前端
 * 做源码解析，对 Kotlin 2.4 的新语法有实际的解析失败风险。真正阻塞式的格式/规则
 * 门禁是根 build.gradle.kts 里一次性 apply 的 spotless + ktlint（按文件 glob 工作，
 * 天然跨模块，这也是它不适合放在这个 per-module convention plugin 里的原因）。
 */
class SanatoQualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
                allRules = false
                ignoreFailures = true // advisory：出报告，不挡构建
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            }
        }
}
