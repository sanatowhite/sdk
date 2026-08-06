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

## 已知限制 / 不要做的事

- 这份约束清单和根 `build.gradle.kts` 的 `sdkModules`(去掉 `sdk-bom` 自己)必须保持一致,漂移由 `verifySdkBomConstraints` 任务机械检查——加新模块时两处都要改。
- 不要给这个模块加任何依赖/代码——它存在的唯一理由是纯版本约束,一旦携带代码就失去"BOM 应该是零副作用"的意义。
