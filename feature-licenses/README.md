# :feature-licenses

## 这是什么 / 不是什么

开源许可清单页——展示 [AboutLibraries](https://github.com/mikepenz/AboutLibraries) 插件离线生成的依赖许可信息。无状态 Composable,不需要 Hilt。

**不是**:不生成许可数据本身(那是 AboutLibraries Gradle 插件的工作,消费方自己在 app 模块 apply),这个模块只负责展示。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:feature-licenses:1.0.0")
}
```

前置条件(消费方自己做一次,不是这个模块的责任):在自己的 app 模块

```kotlin
plugins {
    id("com.mikepenz.aboutlibraries.plugin")
}

aboutLibraries {
    offlineMode = true   // 推荐:构建期不联网抓 license,CI 不会因此变成不确定的网络依赖
}
```

插件会在编译期生成一个 raw 资源(默认 `R.raw.aboutlibraries`)。

## 挂进导航

```kotlin
NavHost(navController, startDestination = Home) {
    composable<Home> { ... }
    licensesGraph(navController, librariesRawRes = R.raw.aboutlibraries)
}
```

或者不用导航,直接用无状态 Screen:

```kotlin
LicensesScreen(librariesRawRes = R.raw.aboutlibraries, onNavigateBack = { ... })
```

## 公开 API

- `LicensesScreen(librariesRawRes: @RawRes Int, onNavigateBack)` — 无状态 Composable。
- `licensesGraph(navController, librariesRawRes)` — 挂进消费方 `NavHost` 的扩展函数。
- `LicensesRoute` — `@Serializable data object` 路由类型。

## 已知限制 / 不要做的事

- 这个模块刻意不接入 `apiCheck`——和 `:core-ui`/`:feature-settings` 同样的理由,公开签名里是 `@Composable` 函数,Compose 编译器版本漂移是噪音不是信号。
- 标题文案 `appkit_licenses_title` 走标准资源覆盖机制,消费方自己声明同名 key 即可改文案/加语言,不用改源码。
