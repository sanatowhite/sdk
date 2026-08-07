package buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project

/**
 * 只配置真正跨 Library/Application 共通、且直接挂在 CommonExtension 本身的字段。
 *
 * ⚠️ 实测发现（AGP 9.3.1）：`defaultConfig { }` / `compileOptions { }` / `packaging { }`
 * 这几个 block 级 DSL 已经不在 CommonExtension 这个共享基类上了——CommonExtension
 * 去掉泛型参数之后收得比预期更窄，这几个方法只在具体的 LibraryExtension /
 * ApplicationExtension 上才能解析到。所以 minSdk / compileOptions / packaging
 * 挪到各自的 convention plugin 里对具体类型直接配置，这里只留 compileSdk。
 *
 * `coreLibraryDesugaring` 依赖【不】放在这里——它曾经是共通的，但发布 SDK 之后
 * library 模块（`sanato.android.library`）刻意关掉了 desugaring（全仓零处使用
 * `java.time`/`java.util.stream`/`Optional`，开着是纯白开销，且给消费方留下隐性
 * 契约：库字节码一旦引用被 desugar 的 API，消费方不开同款开关就是运行期
 * `NoClassDefFoundError`）。只有 `:app`（`sanato.android.application`）还需要它，
 * 依赖声明挪到那个 convention plugin 里，和 `isCoreLibraryDesugaringEnabled` 的
 * 开关放在一起，避免两处独立漂移。
 */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.compileSdk = libs.ver("compileSdk").toInt()
}
