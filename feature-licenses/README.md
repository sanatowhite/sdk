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

## AI 接入指南(可直接执行)

**要不要用这个模块**:需要一个"开源许可清单"页面时加(应用商店合规通常要求这个)。

**接入步骤**:
1. 在自己的 app 模块 apply `com.mikepenz.aboutlibraries.plugin`(见上方代码块)——这是**前置条件**,漏了这一步 `R.raw.aboutlibraries` 不存在,编译会报找不到资源。
2. 加坐标(见上方)。
3. `licensesGraph(navController, librariesRawRes = R.raw.aboutlibraries)` 挂进 `NavHost`,把 `onNavigateToLicenses = { navController.navigate(LicensesRoute) }` 传给 `:feature-settings` 的 `settingsGraph`(如果同时用了 `:feature-settings`)。

**验证**:`./gradlew :app:assembleDebug` 编译通过;运行时进许可页,确认列表里出现真实依赖的许可信息(不是空列表——空列表通常意味着第 1 步的插件没配对)。

**不要做的事**:不要跳过第 1 步直接传一个假的 `librariesRawRes` 值——运行时会崩(资源不存在)或显示空列表(资源存在但没有真实数据)。

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
