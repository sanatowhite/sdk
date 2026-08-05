import buildlogic.lib
import buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * 只在 `:app` apply 这个插件（决策：core-* 不依赖 Hilt，组装全在 app 层）。
 * kapt 与内置 Kotlin 不兼容，走 KSP，不用 `com.android.legacy-kapt`
 * （那是 AGP 10 会删掉的临时方案）。
 */
class SanatoAndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            dependencies {
                add("implementation", libs.lib("hilt-android"))
                add("ksp", libs.lib("hilt-compiler"))
                add("implementation", libs.lib("hilt-navigation-compose"))
                add("testImplementation", libs.lib("hilt-android-testing"))
                add("kspTest", libs.lib("hilt-compiler"))
            }
        }
}
