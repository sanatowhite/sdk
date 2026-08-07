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

## AI 接入指南(可直接执行)

**要不要用这个模块**:需要显式控制"哪些初始化逻辑在 `Application.onCreate` 同步跑、哪些等首帧画完再跑"时用——典型场景是接了 `:core-telemetry`(它的四个采集器就是靠这套编排驱动的)。只是想要一个普通 `Application` 类、没有启动顺序诉求的话不需要。

**接入步骤(用 Hilt,推荐)**:
1. 加坐标:`implementation("com.github.sanatowhite.sdk:core-init-hilt:1.0.0")`(会传递依赖到这个模块)。
2. 自己的 `Application` 继承 `HiltInitializingApplication`(或它的子类 `:core-telemetry-hilt` 的 `TelemetryApplication`,后者已经帮你覆盖好 `onPreDiSetup`)。
3. 具体初始化逻辑写成 `@Inject constructor` 的 `AppInitializer` 实现,标 `@Eager` 或 `@Deferred`,再用 `@Binds @IntoSet` 绑进 `Set<AppInitializer>`——不需要手动调用它们,基类的 `onCreate()` 自动跑。

**接入步骤(不用 Hilt)**:
1. 加坐标:`implementation("com.github.sanatowhite.sdk:core-init:1.0.0")`。
2. 参考本文件"一行接入"里的代码块,在自己的 `Application.onCreate()` 里手动构造 `AppInitializers(eagerInitializers, deferredInitializers)` 并调用 `runEager`/`runDeferred`。

**验证**:装上 debug 包启动一次,确认 `Eager` 标记的初始化逻辑在 `onCreate` 期间就执行完(加个 log 看时序);`Deferred` 的应该在首帧渲染完之后才出现对应 log。

**不要做的事**:不要在具体 `AppInitializer` 实现里调用 `registerActivityLifecycleCallbacks` 或任何自注册手段——执行顺序应该完全由 `AppInitializers` 这一处代码决定,不要散落副作用。

## 公开 API

- `AppInitializer` — `fun interface { fun init(application: Application) }`,具体实现自己不做任何自注册,执行顺序统一由 `AppInitializers` 驱动。
- `Eager` / `Deferred` — `javax.inject.Qualifier` 注解,标记初始化时机:`Eager` 在 `Application.onCreate()` 内同步执行;`Deferred` 首帧绘制完成后才执行。
- `AppInitializers(eagerInitializers, deferredInitializers)` — `@Inject` 构造函数,`runEager(application)` / `runDeferred(application)` 两个入口。
- `FirstFrame.onFirstDraw(application, action)` — 用第一个 Activity 窗口的 `ViewTreeObserver.OnDrawListener` 判定首帧绘制完成,`action` 只触发一次。

## 已知限制 / 不要做的事

- **不要**用 `androidx.startup:startup-runtime` 替代这套编排——它自己注册一个 `ContentProvider`,会污染 `FirstFrame` 正要测量的启动窗口。
- **不要**在具体 `AppInitializer` 实现里调用 `registerActivityLifecycleCallbacks` 或任何形式的自注册——执行顺序应该是 `AppInitializers` 这一处可读的代码,不是散落在各处的副作用。
