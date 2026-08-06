# :core-telemetry

## 这是什么 / 不是什么

薄采集层 + `Telemetry` 抽象:启动耗时(冷/温分类 + TTID + TTFD 挂钩)、按屏幕聚合的卡顿(JankStats)、崩溃(链式 handler + 延迟上报)、ANR(API 30+ `ApplicationExitInfo` 回捞 / API 24-29 前台异常退出信标)、内存(廉价 `Runtime.*` + 昂贵 `Debug.MemoryInfo` 限定时刻)。

**不是**:不含任何具体云端上报实现——`LogcatTelemetry`/`NoOpTelemetry`/`CompositeTelemetry`/`SamplingTelemetry` 都是通用组合子,真正对接 Firebase 的实现在独立的 `:telemetry-firebase` 模块(默认不参与构建)。不自行注册到任何生命周期——所有采集器的启动顺序统一由 `:app` 的 `AppInitializers`(Eager/Deferred 两组)驱动,本模块只提供"能被驱动的类",不做自注册。

## 独立引入

```kotlin
dependencies {
    implementation(project(":core-telemetry")) // 会连带 project(":core-common")
}
```

传递依赖:`androidx.metrics:metrics-performance`(JankStats)。没有其他重量级依赖——崩溃/ANR/内存/启动耗时采集全部基于 Android 框架自带 API,不需要任何第三方 SDK。

独立复用时需要自己驱动:`StartupTracker.start()` 必须在 `Application.onCreate` 早期调用(见 `:app` 的 `AppInitializers` Eager 组);`AppStartTime.record(context)` + 崩溃 handler 的安装必须在 `Application.attachBaseContext` 里、比任何 DI 组装更早调用(参考 `:app` 的 `AppTemplateApp.attachBaseContext`)。

## 公开 API

- `Telemetry` — 核心接口:`event()`(逃生口)+ `screenView`/`startup`/`frame`/`networkRequest`/`crash`/`anr`(固定 schema)。`isEnabled` 配合内联的 `eventIfEnabled` 保证关闭态零分配。
- `NoOpTelemetry` / `LogcatTelemetry` / `CompositeTelemetry(backends)`(故障隔离,一个后端异常不拖垮其他)/ `SamplingTelemetry(delegate, sampleRate, randomValue)`(会话级采样,崩溃永不采样)。
- `AppForegroundState(application)` — 前台/后台状态的唯一真源(基于 started-Activity 计数,与 `ProcessLifecycleOwner` 同源信号)。
- `startup.AppStartTime` / `startup.StartupTracker` — 冷/温启动分类 + TTID,含后台启动否决 + 非主进程否决两道过滤。
- `crash.CrashRecorder` — 链式安装 `Thread.UncaughtExceptionHandler`,崩溃现场只做同步文件写,下次启动通过 `drainPendingCrashReports` 取出上报。
- `anr.AnrExitInfoReaper`(API 30+)/ `anr.ForegroundExitBeacon`(API 24-29)。
- `memory.MemorySampler` — 前台 30s 周期廉价采样 + 三个明确时刻(冷启动完成/应用进后台/系统低内存)的 `Debug.MemoryInfo` 采样。
- `jank.ScreenJankReporter` — 每 Activity/Window 一个实例,用 `PerformanceMetricsState` 给帧数据打屏幕名标签,按屏幕聚合上报。

## 已知限制 / 不要做的事

- **不要**用 `androidx.startup:startup-runtime` 初始化这里的任何采集器——它自己注册一个 `ContentProvider`,等于往正要测量的 provider 初始化窗口里再塞一个 provider。统一走 `:app` 的显式 `Application.onCreate`/`attachBaseContext`。
- **不要**定时采集 `Debug.MemoryInfo`——`getProcessMemoryInfo` 很贵,且 API 29+ 起被系统限流 5 分钟一次;`MemorySampler` 只在冷启动完成/进后台/系统低内存三个时刻采集昂贵档,其余时间只用几乎零开销的 `Runtime.*`。
- **不要**写看门狗线程去检测 ANR——主线程轮询自身本身就有性能开销,且自己可能成为 ANR 的一部分。`ForegroundExitBeacon` 是零运行时开销的启发式方案,聚合统计交给 Play Vitals。
- **不要**在 `FrameReport` 里暴露 `androidx.metrics` 的 `FrameData`——那会让所有 `Telemetry` 实现方被迫依赖 JankStats;`FrameReport` 只包含聚合后的原始类型字段。
- `SamplingTelemetry.crash()` 永远无条件转发,不受采样影响——采样掉崩溃报告等于系统性低估崩溃率。
