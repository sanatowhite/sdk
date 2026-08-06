# :core-ui

## 这是什么 / 不是什么

模板的 Compose 设计系统:M3 主题(含动态取色)+ 间距 token + 通用状态组件(加载/空/错误)+ 页面壳层。

**不是**:不含任何业务页面(设置页、关于页等在 `:app` 里),不含导航逻辑,不依赖 `core-net`/`core-data`(纯 UI 组件库,粘合发生在 `:app`)。

## 独立引入

```kotlin
dependencies {
    implementation(project(":core-ui")) // 会连带需要 project(":core-common")
}
```

会带来的传递依赖:Compose BOM 2026.06.01 家族(`ui`/`ui-graphics`/`ui-tooling-preview`/`material3`)+ `material-icons-core`(**不是** `-extended`,那个未做 R8 时会让 APK 体积 +10MB)。这些是 Compose M3 项目本来就需要的依赖,不算"意外"引入。

如果只想要主题/token 而不想要整个组件库,`theme/` 和 `components/` 是两个独立目录,复制其中一个到自己项目也能编译(`components/StateComponents.kt` 依赖 `core-common` 的 `UiState`,复制时要么一并复制 `UiState`,要么改用自己的加载态类型)。

## 公开 API

- `AppTemplateTheme(darkTheme, dynamicColor, content)` — 主题入口,`dynamicColor` 仅在 API 31+ 生效,24-30 自动回退品牌 seed color。
- `AppTheme.spacing` — `Composable` 属性,取 `Spacing`(xs/sm/md/lg/xl 五档 `Dp`)。
- `AppScaffold(topBar, snackbarHostState, content)` — 统一页面壳层,`contentWindowInsets` 已设为 `WindowInsets.safeDrawing`。
- `LoadingState` / `EmptyState(message)` / `ErrorState(message, onRetry)` / `StateContent(state: UiState<T>, onRetry, content)` — 后者是 `UiState` 的通用渲染器,四态优先级 loading > error > empty > content。

## 已知限制 / 不要做的事

- **不要**引入 `material-icons-extended`——core 集已覆盖本模块用到的图标,extended 包在未开 R8 shrinking 的场景下会显著拉大产物体积。
- **不要**在这里加 `WindowSizeClass`/自适应布局或 Paging3 骨架屏——这两项在设计裁决里明确排除,模板只做"基础层"。
- **不要**在 `Theme.kt` 里为主题变化反复调用 `enableEdgeToEdge`——那是 Activity 层的职责(`:app` 的 `MainActivity`),`SystemBarStyle` 的颜色/scrim 参数在 targetSdk 36+ 上已基本失效,主题只需要跟随切换状态栏图标明暗,不属于 `:core-ui` 该管的范围。
- 动效统一用 `MaterialTheme.motionScheme`(M3 1.4.0 起提供),不要在这里自建一套动效 token。
