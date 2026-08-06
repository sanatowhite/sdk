# :core-common

## 这是什么 / 不是什么

模板里所有其他 `core-*` 模块的公共地基:通用结果类型(`AppResult`)、UI 组合态(`UiState`)、协程 dispatcher 的 qualifier 注解。

**不是**:不含任何 Android Context 相关代码,不含网络/存储实现,不依赖 Hilt(只依赖 `javax.inject` 的 `@Qualifier`)。任何具体能力(网络请求、DataStore、遥测采集)都在各自的 `core-net`/`core-data`/`core-telemetry` 里,不会反向依赖这个模块之外的任何东西。

## 独立引入

```kotlin
// settings.gradle.kts
include(":core-common")

// 消费方 build.gradle.kts
dependencies {
    implementation(project(":core-common"))
}
```

无额外传递依赖需要关心——本模块只带 `androidx.core:core-ktx`、`kotlinx-coroutines-android`、`javax.inject`,都是最小化的公共基础库,不会给消费方引入任何"意外"的库。

如果不走 fork/多模块场景,直接复制 `src/main/kotlin/io/sanato/apptemplate/core/common/` 整个目录到自己项目也可以——三个文件都是纯 Kotlin,无 Android 特有 API,甚至能在纯 JVM 模块里编译通过。

## 公开 API

- `AppResult<T>` — sealed interface,`Success<T>` / `Failure`,附带 `map`/`onSuccess`/`onFailure` 内联扩展。
- `UiState<T>` — data class(`data`/`isLoading`/`error` 三个可空/布尔字段),配 `loading()`/`success()`/`failure()` 三个静态构造帮助函数。
- `IoDispatcher` / `DefaultDispatcher` / `MainImmediateDispatcher` — `javax.inject.Qualifier` 注解,用于标记 `CoroutineDispatcher` 的注入点;具体 dispatcher 由 `:app` 的 Hilt Module 提供,本模块只定义"契约"。

## 已知限制 / 不要做的事

- **不要**把 `UiState` 改成 sealed class 层级——"加载中但已有旧数据"这类组合态在 sealed 建模下会需要额外的中间态类型,且字段一多就指数爆炸。data class + 可空字段是有意为之的选择。
- **不要**在这里 apply Hilt 插件或依赖 `hilt-android`——`core-*` 模块的设计是只认 `javax.inject` 契约,真正的 DI 组装(Component、Module)统一收在 `:app`,便于单独抽取这些模块时不被迫拖入 Hilt 运行时。
- `AppResult.Failure` 只携带 `Throwable`,不携带具体错误分类(HTTP 状态码等)——那属于 `:core-net` 的 `AppError`,会在网络层把 `Throwable` 精确分类之后再包进 `AppResult`。
