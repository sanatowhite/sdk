# :core-init

## 这是什么 / 不是什么

启动编排的通用原语:`AppInitializer` 接口(`@Eager`/`@Deferred` 两个 qualifier)+ 驱动它们的 `AppInitializers` + 首帧回调 `FirstFrame`。

**不是**:不含任何具体采集器实现——那些在 `:core-telemetry` 里。不含 Hilt 绑定——那是 `:core-init-hilt` 的职责,这个模块只依赖 `javax.inject`。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-init:1.0.0")
}
```

不用 Hilt 就直接手动构造:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val initializers = AppInitializers(
            eagerInitializers = setOf(/* 你自己的 AppInitializer 实现 */),
            deferredInitializers = setOf(/* ... */),
        )
        initializers.runEager(this)
        FirstFrame.onFirstDraw(this) { initializers.runDeferred(this) }
    }
}
```

用 Hilt 就加 `:core-init-hilt`,拿现成的 `HiltInitializingApplication` 基类。

## 公开 API

- `AppInitializer` — `fun interface { fun init(application: Application) }`,具体实现自己不做任何自注册,执行顺序统一由 `AppInitializers` 驱动。
- `Eager` / `Deferred` — `javax.inject.Qualifier` 注解,标记初始化时机:`Eager` 在 `Application.onCreate()` 内同步执行;`Deferred` 首帧绘制完成后才执行。
- `AppInitializers(eagerInitializers, deferredInitializers)` — `@Inject` 构造函数,`runEager(application)` / `runDeferred(application)` 两个入口。
- `FirstFrame.onFirstDraw(application, action)` — 用第一个 Activity 窗口的 `ViewTreeObserver.OnDrawListener` 判定首帧绘制完成,`action` 只触发一次。

## 已知限制 / 不要做的事

- **不要**用 `androidx.startup:startup-runtime` 替代这套编排——它自己注册一个 `ContentProvider`,会污染 `FirstFrame` 正要测量的启动窗口。
- **不要**在具体 `AppInitializer` 实现里调用 `registerActivityLifecycleCallbacks` 或任何形式的自注册——执行顺序应该是 `AppInitializers` 这一处可读的代码,不是散落在各处的副作用。
