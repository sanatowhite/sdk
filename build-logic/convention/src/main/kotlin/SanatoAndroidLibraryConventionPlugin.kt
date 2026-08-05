import buildlogic.configureAndroidCommon
import buildlogic.lib
import buildlogic.libs
import buildlogic.ver
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `:core-*` 模块的基础 convention plugin。
 *
 * 刻意【不】apply `org.jetbrains.kotlin.android` —— AGP 9 内置 Kotlin，apply 它会报
 * "Cannot add extension with name 'kotlin', as there is an extension already registered
 * with that name"。KGP 的版本由根 build.gradle.kts 的 buildscript classpath 提供。
 *
 * `:updatechecker` 不套用这个插件——它是对外发布的库，套上 convention plugin
 * 很容易被这里未来的改动意外影响到发布产物（见 CLAUDE.md 的铁律）。
 */
class SanatoAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("sanato.quality")

            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)

                defaultConfig {
                    minSdk = libs.ver("minSdk").toInt()
                    // library 模块不设 targetSdk —— AGP 9 已弃用，且默认跟随 compileSdk。
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                    isCoreLibraryDesugaringEnabled = true
                }
                // 不设 kotlin.compilerOptions.jvmTarget —— 内置 Kotlin 下它默认跟随
                // compileOptions.targetCompatibility。

                packaging {
                    resources.excludes +=
                        setOf(
                            "/META-INF/{AL2.0,LGPL2.1}",
                            "/META-INF/LICENSE*",
                            "DebugProbesKt.bin",
                        )
                }

                testFixtures.enable = true
            }

            dependencies {
                add("implementation", libs.lib("androidx-core-ktx"))
                add("implementation", libs.lib("kotlinx-coroutines-android"))
                // core-* 模块要 DI 友好但不依赖 Hilt 插件/运行时（决策：组装全在 :app）：
                // 只给 javax.inject，@Inject constructor 能写，Component 组装留给 :app。
                add("implementation", libs.lib("javax-inject"))

                add("testImplementation", libs.lib("junit4"))
                add("testImplementation", libs.lib("kotlinx-coroutines-test"))
                add("testImplementation", libs.lib("turbine"))
                add("testImplementation", libs.lib("mockk"))
            }
        }
}
