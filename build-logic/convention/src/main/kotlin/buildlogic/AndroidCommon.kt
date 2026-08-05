package buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * 只配置真正跨 Library/Application 共通、且直接挂在 CommonExtension 本身的字段。
 *
 * ⚠️ 实测发现（AGP 9.3.1）：`defaultConfig { }` / `compileOptions { }` / `packaging { }`
 * 这几个 block 级 DSL 已经不在 CommonExtension 这个共享基类上了——CommonExtension
 * 去掉泛型参数之后收得比预期更窄，这几个方法只在具体的 LibraryExtension /
 * ApplicationExtension 上才能解析到。所以 minSdk / compileOptions / packaging
 * 挪到各自的 convention plugin 里对具体类型直接配置，这里只留 compileSdk。
 */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.compileSdk = libs.ver("compileSdk").toInt()

    dependencies {
        add("coreLibraryDesugaring", libs.lib("android-desugarJdkLibs"))
    }
}
