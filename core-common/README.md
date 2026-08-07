# :core-common

## 这是什么 / 不是什么

模板里所有其他 `core-*` 模块的公共地基:通用结果类型(`AppResult`)、UI 组合态(`UiState`)、协程 dispatcher 的 qualifier 注解、零配置构建信息(`AppBuildInfo`)、`isDebuggableBuild()`。

**不是**:不含任何具体能力实现,不依赖 Hilt(只依赖 `javax.inject` 的 `@Qualifier`)——想要开箱即用的 Hilt 绑定(dispatcher provider、`AppBuildInfo` provider),依赖 `:core-common-hilt`。任何具体能力(网络请求、DataStore、遥测采集)都在各自的 `core-net`/`core-data`/`core-telemetry` 里。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-common:1.0.0")
}
```

不需要改我们的任何源码,`implementation` 一行就能用。无额外传递依赖需要关心——本模块只带 `androidx.core:core-ktx`、`kotlinx-coroutines-android`、`javax.inject`,都是最小化的公共基础库,不会给消费方引入任何"意外"的库。

## 公开 API

- `AppResult<T>` — sealed interface,`Success<T>` / `Failure`,附带 `map`/`onSuccess`/`onFailure` 内联扩展。
- `UiState<T>` — data class(`data`/`isLoading`/`error` 三个可空/布尔字段),配 `loading()`/`success()`/`failure()` 三个静态构造帮助函数。
- `IoDispatcher` / `DefaultDispatcher` / `MainImmediateDispatcher` — `javax.inject.Qualifier` 注解,用于标记 `CoroutineDispatcher` 的注入点;不用 Hilt 就自己 new 对应的 `Dispatchers.*`,用 Hilt 就加 `:core-common-hilt` 拿现成 provider。
- `AppBuildInfo` — data class(`applicationId`/`appLabel`/`versionName`/`versionCode`/`gitSha`/`buildTimeMillis`),`AppBuildInfo.fromPackageManager(context)` 零配置从 `PackageManager` 读取;`gitSha`/`buildTimeMillis` 想要真实值,用 `:core-common-hilt` 的可选覆盖绑定。
- `Context.isDebuggableBuild()` — 读 `ApplicationInfo.FLAG_DEBUGGABLE`,库模块判断"是不是调试构建"的标准方式(拿不到消费方的 `BuildConfig.DEBUG`)。

## 已知限制 / 不要做的事

- **不要**把 `UiState` 改成 sealed class 层级——"加载中但已有旧数据"这类组合态在 sealed 建模下会需要额外的中间态类型,且字段一多就指数爆炸。data class + 可空字段是有意为之的选择。
- **不要**在这里 apply Hilt 插件或依赖 `hilt-android`——`core-*` 模块的设计是只认 `javax.inject` 契约,真正的 DI 组装(Component、Module)收在各自的 `-hilt` 伴生模块,便于不用 Hilt 的消费方也能用。
- `AppResult.Failure` 只携带 `Throwable`,不携带具体错误分类(HTTP 状态码等)——那属于 `:core-net` 的 `AppError`,会在网络层把 `Throwable` 精确分类之后再包进 `AppResult`。
