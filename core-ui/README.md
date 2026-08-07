# :core-ui

## 这是什么 / 不是什么

模板的 Compose 设计系统:M3 主题(含动态取色)+ 间距 token + 通用状态组件(加载/空/错误)+ 页面壳层。

**不是**:不含任何业务页面(设置页、关于页等在 `:feature-settings` 里),不含导航逻辑,不依赖 `core-net`/`core-data`(纯 UI 组件库,粘合发生在装配层)。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-ui:1.0.0")
}
```

不需要改我们的任何源码——`api()` 已经把 `core-common` 和用到的 Compose 坐标(runtime/ui/foundation-layout/material3 + BOM)传递给消费方,`implementation` 一行就能用,不用自己再声明这些依赖。会带来的传递依赖:Compose BOM 家族 + `material-icons-core`(**不是** `-extended`,那个未做 R8 时会让 APK 体积 +10MB)。这些是 Compose M3 项目本来就需要的依赖,不算"意外"引入。

页面跟随消费方自己的 `MaterialTheme`,不强制套 `AppTemplateTheme`——`AppTheme.spacing` 有默认值,不套主题也能用,想要品牌 seed color + 动态取色再用 `AppTemplateTheme`。

## AI 接入指南(可直接执行)

**要不要用这个模块**:几乎所有 Compose 页面都会间接需要它——`feature-settings`/`feature-feedback`/`feature-licenses`/`feature-update` 全部依赖它做壳层/状态组件。单独引入的场景是"只要统一的加载/空/错误状态组件和主题,不要任何具体功能页面"。

**接入步骤**:
1. 加坐标:
   ```kotlin
   dependencies {
       implementation("com.github.sanatowhite.sdk:core-ui:1.0.0")
   }
   ```
2. (可选)在 `setContent {}` 最外层套主题:
   ```kotlin
   setContent {
       AppTemplateTheme(darkTheme = isSystemInDarkTheme()) {
           // 你自己的 Compose 内容
       }
   }
   ```
   不套也能用,`AppTheme.spacing` 有默认值。
3. 页面壳层用 `AppScaffold(topBar = {...}) { padding -> ... }`;加载/空/错误状态用 `StateContent(state = uiState, onRetry = {...}) { data -> ... }`。

**验证**:`./gradlew :<your-module>:compileDebugKotlin` 编译通过。这个模块**没有** `apiCheck`(见下面"已知限制"),不要以为跑了 `apiCheck` 就验证了 API 兼容性——真正的兜底是 `checks/consumer-smoke`。

**不要做的事**:不要引入 `material-icons-extended`(会显著拉大产物体积,`core` 集已够用);不要给这个模块加 `WindowSizeClass`/自适应布局(设计上明确排除,见 `TEMPLATE.md`)。

## 公开 API

- `AppTemplateTheme(darkTheme, dynamicColor, content)` — 主题入口,`dynamicColor` 仅在 API 31+ 生效,24-30 自动回退品牌 seed color。
- `AppTheme.spacing` — `Composable` 属性,取 `Spacing`(xs/sm/md/lg/xl 五档 `Dp`)。
- `AppScaffold(topBar, snackbarHostState, content)` — 统一页面壳层,`contentWindowInsets` 已设为 `WindowInsets.safeDrawing`。
- `LoadingState` / `EmptyState(message)` / `ErrorState(message, onRetry)` / `StateContent(state: UiState<T>, onRetry, content)` — 后者是 `UiState` 的通用渲染器,四态优先级 loading > error > empty > content。

## 已知限制 / 不要做的事

- **不要**引入 `material-icons-extended`——core 集已覆盖本模块用到的图标,extended 包在未开 R8 shrinking 的场景下会显著拉大产物体积。
- **不要**在这里加 `WindowSizeClass`/自适应布局或 Paging3 骨架屏——这两项在设计裁决里明确排除,模板只做"基础层"。
- **不要**在 `Theme.kt` 里为主题变化反复调用 `enableEdgeToEdge`——那是 Activity 层的职责,`SystemBarStyle` 的颜色/scrim 参数在 targetSdk 36+ 上已基本失效,主题只需要跟随切换状态栏图标明暗,不属于 `:core-ui` 该管的范围。
- 动效统一用 `MaterialTheme.motionScheme`(M3 1.4.0 起提供),不要在这里自建一套动效 token。
- 这个模块刻意不接入 `apiCheck`(Compose 编译器注入的 `Composer`/`$$changed` 参数会随编译器版本整体漂移,是噪音不是信号)——API 稳定性由 consumer-smoke 独立工程兜底。
