import buildlogic.lib
import buildlogic.libs
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Roborazzi 截图测试：全部跑在 Robolectric 上（JVM，无模拟器）。
 *
 * `isIncludeAndroidResources = true` 是前提；`robolectric.properties` 的
 * `sdk=35` 兜底写在各模块的 src/test/resources 里（不是这里）——因为 Robolectric
 * 4.16.1 的上限是 SDK 36 且 SDK 36 需要 JDK 21，而本仓库 CI/本地统一用 JDK 17，
 * 所以固定在 35，不依赖 targetSdk/compileSdk 的默认推导。
 *
 * 只对 LibraryExtension 配置（这里主要给 :core-ui 用）——`testOptions` 这个 block
 * 在 AGP 9.3.1 下也不在共享的 CommonExtension 上，只在具体类型上才能解析到。
 * 如果将来 :app 也要接这个插件，需要另外加一段针对 ApplicationExtension 的分支。
 */
class SanatoAndroidRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")

            extensions.configure<LibraryExtension> {
                testOptions {
                    unitTests {
                        isIncludeAndroidResources = true
                    }
                }
            }

            dependencies {
                add("testImplementation", libs.lib("roborazzi"))
                add("testImplementation", libs.lib("roborazzi-compose"))
                add("testImplementation", libs.lib("roborazzi-junit-rule"))
                add("testImplementation", libs.lib("robolectric"))
                add("testImplementation", platform(libs.lib("androidx-compose-bom")))
                add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
                add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
            }
        }
}
