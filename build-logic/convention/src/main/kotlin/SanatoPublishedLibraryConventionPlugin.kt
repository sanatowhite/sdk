import buildlogic.readSdkCoordinates
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register

abstract class SanatoPublishExtension {
    /** 默认取 project.name（= 目录名），只有想让 artifactId 和目录名不一致时才设。 */
    abstract val artifactId: Property<String>
    abstract val description: Property<String>
}

/**
 * 可发布 Android library 的【叠加】插件。
 *
 * 刻意不 apply `com.android.library` / `sanato.android.library`：这个插件要能同时
 * 贴到 `sanato.android.library`、`sanato.android.library.compose`，以及完全不用
 * convention plugin 的 `:updatechecker` 上。它只做四件事：`maven-publish`、release
 * 单变体 + sources jar、artifactId/POM、坐标——不加任何 dependency、不碰
 * `defaultConfig`、不碰 `consumerProguardFiles`。这正是 `:updatechecker` 敢接入它
 * 而不违反"不用 convention plugin"这条铁律原意的全部理由：那条铁律真正要挡的是
 * "convention plugin 悄悄往发布产物里塞依赖/改配置"，这个插件结构上做不到这件事。
 */
class SanatoPublishedLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            val ext = extensions.create<SanatoPublishExtension>("sanatoPublish")

            pluginManager.apply("maven-publish")

            val coords = readSdkCoordinates()
            group = coords.group
            version = coords.version

            // withPlugin：让本插件在 plugins {} 块里的书写顺序无关紧要——它在
            // com.android.library 被 apply 的那一刻触发，早于任何 afterEvaluate。
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> {
                    publishing {
                        singleVariant("release") {
                            withSourcesJar()
                            // 不 withJavadocJar()：Kotlin 源码产出的 javadoc jar 基本是空壳，
                            // JitPack 不强制要求（那是 Maven Central 的规矩），多一个 artifact
                            // 只会让 ~/.m2 的产物列表更难核对。
                        }
                    }
                }
            }

            afterEvaluate {
                extensions.configure<PublishingExtension> {
                    publications.register<MavenPublication>("release") {
                        from(components["release"])
                        artifactId = ext.artifactId.getOrElse(project.name)
                        pom {
                            name.set(artifactId)
                            ext.description.orNull?.let { description.set(it) }
                            url.set("https://github.com/sanatowhite/sdk")
                            licenses {
                                license {
                                    name.set("Apache-2.0")
                                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                                }
                            }
                            scm {
                                url.set("https://github.com/sanatowhite/sdk")
                                connection.set("scm:git:https://github.com/sanatowhite/sdk.git")
                            }
                        }
                    }
                }
            }
        }
}
