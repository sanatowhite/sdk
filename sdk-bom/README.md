# :sdk-bom

## 这是什么 / 不是什么

一个 `java-platform`(Maven BOM)——约束所有 SDK 模块的版本对齐,消费方引入之后各模块坐标就不用再写版本号。

**不是**:不含任何代码,纯版本约束清单;不参与 `verifyModuleGraph`(没有 `project()` 依赖可检查),但参与 `verifySdkModuleList`(它 apply 了 `maven-publish`)和它自己专属的 `verifySdkBomConstraints`。

## 一行接入

```kotlin
dependencies {
    implementation(platform("com.github.sanatowhite.sdk:sdk-bom:1.0.0"))

    implementation("com.github.sanatowhite.sdk:core-ui")
    implementation("com.github.sanatowhite.sdk:core-data")
    implementation("com.github.sanatowhite.sdk:feature-settings")
    // ... 其余模块同理,不用再写版本号
}
```

不引入这个 BOM 也完全没问题——每个模块本来就能独立指定版本号,`sdk-bom` 只是"想让多个模块保持同一个版本"这件事的便利写法。

## AI 接入指南(可直接执行)

**要不要用这个模块**:消费方依赖 3 个以上本仓库 SDK 模块时推荐加,减少"某个模块忘记升级版本号导致混用不同版本"的风险。只依赖 1-2 个模块时加不加都行。

**接入步骤**:
1. `implementation(platform("com.github.sanatowhite.sdk:sdk-bom:1.0.0"))` 放在 `dependencies {}` 块最前面。
2. 其余每个 `com.github.sanatowhite.sdk:<module>` 坐标去掉版本号后缀。

**验证**:`./gradlew :<your-module>:dependencies --configuration releaseRuntimeClasspath | grep "com.github.sanatowhite.sdk"`——确认列出的每个模块版本号一致,且等于 BOM 声明的版本。

**不要做的事**:见"已知限制"。

## 已知限制 / 不要做的事

- 这份约束清单和根 `build.gradle.kts` 的 `sdkModules`(去掉 `sdk-bom` 自己)必须保持一致,漂移由 `verifySdkBomConstraints` 任务机械检查——加新模块时两处都要改。
- 不要给这个模块加任何依赖/代码——它存在的唯一理由是纯版本约束,一旦携带代码就失去"BOM 应该是零副作用"的意义。
