# :baselineprofile

## 这是什么 / 不是什么

`com.android.test` 模块(配 `androidx.baselineprofile` 插件),运行 `BaselineProfileGenerator`——一个走一遍冷启动路径的 instrumented test——把结果收集成 ART 可读的 profile 规则,由 `:app` 的 `baselineProfile { }` 消费并复制进 `app/src/release/generated/baselineProfiles/baseline-prof.txt`。这个文件**提交进版本库**,是构建输入而不是可再生的输出(`automaticGenerationDuringBuild = false`)。

**不是**:不是自动化的、每次构建都跑一遍的东西——生成一次很慢(要跑真实的 instrumented test),而且几乎不会因为一次普通代码改动就需要重新生成,只在发版前、或者 UI/启动路径有实质性变化时手动跑。也不做任何性能断言——它只负责"产出一份 profile",验证 profile 是否生效是 `:benchmark` 模块的职责。

## 独立引入

不适用——同 `:benchmark`,只对 `:app` 有意义(`targetProjectPath = ":app"`),不是可独立抽取的能力。

## 公开 API

无——测试模块,不产出被消费的类。`BaselineProfileGenerator` 是唯一的测试类。

## 已知限制 / 不要做的事

- **必须**是真机或 API 28+ 模拟器(`BaselineProfileRule` 的硬要求),尽管 `:app` 本身 `minSdk = 24`。CI 里用 Gradle Managed Device(`workflow_dispatch` 手动触发的 `baseline-profile.yml`,需要开 KVM)。
- **不要**依赖 `androidx.baselineprofile` 1.4.1——已实测在 AGP 9.3.1 下失败(`AndroidTestModuleWrapper` 找不到新的 `TestExtensionImpl` 类型,是插件自身没跟上 AGP 9 的 DSL 改名,不是配置问题)。锁定 **1.5.0-beta01**,这是第一个实测通过的版本。
- 如果这个插件将来又不兼容了,退路是完全弃用插件:AGP 本身支持从 `app/src/<buildType>/baselineProfiles/*.txt` 手工放置的文件读取并打包进 release APK,一次性 `adb pull` + 手工生成也能达到同样效果。插件只是"自动生成 + 自动放置"这一层便利。
- 生成命令:`./gradlew :app:generateReleaseBaselineProfile`(或 `generateStagingBaselineProfile`)。这条命令本身运行需要几分钟,不是 PR 门禁的一部分。
- 只覆盖冷启动这一条路径。如果你的 app 有其他用户高频走的关键路径(比如"打开后立即进设置页"),应该在 `BaselineProfileGenerator` 里补一个对应的 `rule.collect { }` 场景一起录进同一份 profile。
