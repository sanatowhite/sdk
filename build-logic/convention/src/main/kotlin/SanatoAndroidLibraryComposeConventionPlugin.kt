import buildlogic.lib
import buildlogic.libs
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * 需要 Compose 的 core 模块（目前是 :core-ui）叠加这一层。
 *
 * `org.jetbrains.kotlin.plugin.compose` 在内置 Kotlin 下【仍然必须显式 apply】——
 * 官方文档原话是内置 Kotlin 不替代它，已在 Phase 0.5 的 scratch 工程实测确认
 * （AGP 9.3.1 + 这个插件 + buildFeatures.compose 一次编译成功）。
 */
class SanatoAndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("sanato.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }

            dependencies {
                val bom = platform(libs.lib("androidx-compose-bom"))
                add("implementation", bom)
                add("androidTestImplementation", bom)
                add("implementation", libs.lib("androidx-compose-ui"))
                add("implementation", libs.lib("androidx-compose-ui-graphics"))
                add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
                add("implementation", libs.lib("androidx-compose-material3"))
                // M3 1.4.0 起不再传递 material-icons-core，必须显式加。
                // 禁止引入 material-icons-extended（未做 R8 时体积 +10MB）。
                add("implementation", libs.lib("androidx-compose-material-icons-core"))
                add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
            }
        }
}
