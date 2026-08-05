import buildlogic.configureAndroidCommon
import buildlogic.configureSigning
import buildlogic.lib
import buildlogic.libs
import buildlogic.readAppVersion
import buildlogic.ver
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `:app` 专属 convention plugin。签名 / buildTypes / applicationId / 版本号都只在
 * 这里出现一次——`:core-*` library 模块不需要这些概念。
 */
class SanatoAndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("sanato.quality")

            val version = readAppVersion()

            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)

                defaultConfig {
                    applicationId = "io.sanato.apptemplate" // bootstrap.sh 唯一的替换点
                    minSdk = libs.ver("minSdk").toInt()
                    targetSdk = libs.ver("targetSdk").toInt() // 显式写：不承担 compileSdk 37 未验证的行为变更
                    versionCode = version.versionCode
                    versionName = version.versionName
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    vectorDrawables.useSupportLibrary = true
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                    isCoreLibraryDesugaringEnabled = true
                }

                packaging {
                    resources.excludes +=
                        setOf(
                            "/META-INF/{AL2.0,LGPL2.1}",
                            "/META-INF/LICENSE*",
                            "DebugProbesKt.bin",
                        )
                }

                configureSigning(this)

                buildTypes {
                    debug {
                        // 与 release 完全隔离安装，可同机共存、互不覆盖——本地开发验证
                        // "升级"场景（本模板的核心功能）必须靠这个后缀才装得下两个包。
                        applicationIdSuffix = ".debug"
                        versionNameSuffix = "-debug"
                        isMinifyEnabled = false
                        isDebuggable = true
                    }
                    release {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        // R8 full mode 自 AGP 8.0 起默认开启；这里不设
                        // android.enableR8.fullMode=false（那是唯一能调回 compat 模式的开关）。
                        proguardFiles(
                            // AGP 9 新默认 android.r8.proguardAndroidTxt.disallowed=true
                            // 已经不允许用 proguard-android.txt，只能用 -optimize.txt。
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                        signingConfig = signingConfigs.findByName("release")
                            ?: signingConfigs.getByName("debug")
                    }
                    create("staging") {
                        initWith(getByName("release"))
                        applicationIdSuffix = ".staging"
                        versionNameSuffix = "-staging"
                        isDebuggable = false
                        // staging 只存在于 :app；library 模块（core-*）没有这个 buildType，
                        // 没有这行会报 "Unable to find a matching variant of project :core-xxx"。
                        matchingFallbacks += listOf("release")
                        signingConfig = signingConfigs.findByName("release")
                            ?: signingConfigs.getByName("debug")
                    }
                }

                buildFeatures {
                    buildConfig = true
                }
            }

            dependencies {
                add("implementation", libs.lib("androidx-splashscreen"))
            }
        }
}
