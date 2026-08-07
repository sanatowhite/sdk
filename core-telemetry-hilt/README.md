# :core-telemetry-hilt

## 这是什么 / 不是什么

`:core-telemetry` 的 Hilt 装配:`Set<Telemetry>` 的 `@Multibinds` + 默认后端(`Logcat`/`NoOp` 按 `isDebuggableBuild()` 二选一、`RingLogBuffer`、`CompositeTelemetry`)、`AppForegroundState`/`MemorySampler` 的 provider、四个 `AppInitializer` 的 `@Binds @IntoSet` 绑定,以及消费方最终继承的 `TelemetryApplication` 基类。

**不是**:不含任何具体云端上报实现——那是 `:telemetry-firebase`(可选)。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-telemetry-hilt:1.0.0")
}
```

```kotlin
@HiltAndroidApp
class MyApp : TelemetryApplication()
```

两行接入:启动计时、崩溃/ANR 采集、卡顿(需要 Activity 侧接 `ScreenJankReporter`,见 `:core-telemetry` README)、内存采样,`Logcat` 后端在可调试构建下自动开启。

想加自己的后端(除了 `:telemetry-firebase`)?在自己的 app 模块写 4 行:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object MyTelemetryModule {
    @Provides
    @IntoSet
    fun provideMyBackend(application: Application): Telemetry = MyTelemetry(application)
}
```

`CompositeTelemetry` 自动 fan-out,不需要改任何我们的代码。

## 公开 API

- `TelemetryBackendsModule` — `@Module`,`Set<Telemetry>` 的 `@Multibinds` + 四个 `AppInitializer` 的 `@Binds @IntoSet` 绑定(遥测相关的启动追踪/ANR 检查/崩溃上报/内存采样)。
- `TelemetryModule` — `@Module`,`Logcat`/`NoOp` 默认后端、`RingLogBuffer`、`CompositeTelemetry`、`AppForegroundState`、`MemorySampler` 的 provider。
- `TelemetryApplication` — 抽象 `Application` 基类(继承 `:core-init-hilt` 的 `HiltInitializingApplication`),覆盖 `onPreDiSetup` 装崩溃 handler + 启动计时。消费方需要在这之上再加自己的初始化,记得调用 `super.onPreDiSetup(application)`。

## 已知限制 / 不要做的事

- **不要**指望 `Set<Telemetry>` 天然支持"opt-out 某个默认后端"——Hilt 的 `@InstallIn` 模块只要在 classpath 上就无条件安装,`Logcat`/`NoOp`/`RingLogBuffer` 这几条都是加法,不会挡住任何人,但也去不掉(不用这个模块、自己手动构造 `AppInitializers` 是唯一的完全绕过方式)。
- 想追加自己后端时套一层 decorator(比如采样)?目前 `provideTelemetry` 是硬绑定 `CompositeTelemetry`,直接再提供一条 `Telemetry` binding 会撞 duplicate binding——这个场景暂不支持,已知限制。
