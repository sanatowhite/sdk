# :core-telemetry

## 这是什么 / 不是什么

薄采集层 + `Telemetry` 抽象:启动耗时(冷/温分类 + TTID + TTFD 挂钩)、按屏幕聚合的卡顿(JankStats)、崩溃(链式 handler + 延迟上报)、ANR(API 30+ `ApplicationExitInfo` 回捞 / API 24-29 前台异常退出信标)、内存(廉价 `Runtime.*` + 昂贵 `Debug.MemoryInfo` 限定时刻),以及四个 `AppInitializer` 实现(启动编排的驱动单元)。

**不是**:不含任何具体云端上报实现——`LogcatTelemetry`/`NoOpTelemetry`/`CompositeTelemetry`/`SamplingTelemetry` 都是通用组合子,真正对接 Firebase 的实现在独立的 `:telemetry-firebase` 模块。不自行注册到任何生命周期——所有采集器的启动顺序统一由 `:core-init` 的 `AppInitializers`(Eager/Deferred 两组)驱动,本模块只提供"能被驱动的类",不做自注册。不含 Hilt 绑定——那是 `:core-telemetry-hilt` 的职责。

## 一行接入

不用 Hilt:

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-telemetry:1.0.0")
}
```

自己 new 需要的采集器,`AppInitializers`(来自 `:core-init`)手动构造,参考 `:core-telemetry-hilt` 的 `TelemetryModule` 代码抄一遍装配逻辑即可——这条路径完整可用,不需要 Hilt 运行时。

用 Hilt(推荐,开箱即用):

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-telemetry-hilt:1.0.0")
}
```

```kotlin
@HiltAndroidApp
class MyApp : TelemetryApplication()
```

两行接入启动计时、崩溃/ANR 采集、卡顿、内存采样。想加 Firebase 后端?再加一行 `implementation("com.github.sanatowhite.sdk:telemetry-firebase:1.0.0")`,`Set<Telemetry>` 自动多一个后端,不用改代码。

传递依赖:`androidx.metrics:metrics-performance`(JankStats)。没有其他重量级依赖——崩溃/ANR/内存/启动耗时采集全部基于 Android 框架自带 API,不需要任何第三方 SDK。

## AI 接入指南(可直接执行)

**要不要用这个模块**:需要启动耗时/卡顿/崩溃/ANR/内存采集时用。想要真实上报到某个后端(而不是只打 Logcat),额外加 `:telemetry-firebase` 或自己写一个 `Telemetry` 实现。

**接入步骤(用 Hilt,推荐)**:
1. 加坐标:`implementation("com.github.sanatowhite.sdk:core-telemetry-hilt:1.0.0")`。
2. 自己的 `Application` 继承 `TelemetryApplication`,加 `@HiltAndroidApp`。
3. 想采集卡顿,在每个 Activity 里手动接 `ScreenJankReporter(window, telemetry)`(需要注入 `Telemetry`),`onScreenChanged("屏幕名")` 切屏时调用,`onPause()` 里调用 `flush()`。
4. 想要真实上报,额外加 `implementation("com.github.sanatowhite.sdk:telemetry-firebase:1.0.0")`,不需要改代码。

**验证**:装 debug 包,触发一次崩溃(比如 `:debug-tools` 的 `CrashTriggers.triggerCrash()`),重启 app,确认崩溃日志出现在 Logcat(或 Firebase Crashlytics 后台,如果接了 `:telemetry-firebase` 且用了真实 `google-services.json`)。

**不要做的事**:不要用 `androidx.startup` 初始化任何采集器(见下面"已知限制");不要定时采集 `Debug.MemoryInfo`;不要从崩溃 handler 内部直接调用 `Telemetry` 上报(会造成 fatal + non-fatal 双重上报,见 `CLAUDE.md` 的 telemetry 章节)。

## 公开 API

- `Telemetry` — 核心接口:`event()`(逃生口)+ `screenView`/`startup`/`frame`/`networkRequest`/`crash`/`anr`(固定 schema)。`isEnabled` 配合内联的 `eventIfEnabled` 保证关闭态零分配。
- `NoOpTelemetry` / `LogcatTelemetry` / `CompositeTelemetry(backends)`(故障隔离,一个后端异常不拖垮其他)/ `SamplingTelemetry(delegate, sampleRate, randomValue)`(会话级采样,崩溃永不采样)。
- `AppForegroundState(application)` — 前台/后台状态的唯一真源(基于 started-Activity 计数,与 `ProcessLifecycleOwner` 同源信号)。
- `startup.AppStartTime` / `startup.StartupTracker` — 冷/温启动分类 + TTID,含后台启动否决 + 非主进程否决两道过滤。
- `crash.CrashRecorder` — 链式安装 `Thread.UncaughtExceptionHandler`,崩溃现场只做同步文件写,下次启动通过 `drainPendingCrashReports` 取出上报。
- `anr.AnrExitInfoReaper`(API 30+)/ `anr.ForegroundExitBeacon`(API 24-29)。
- `memory.MemorySampler` — 前台 30s 周期廉价采样 + 三个明确时刻(冷启动完成/应用进后台/系统低内存)的 `Debug.MemoryInfo` 采样。
- `jank.ScreenJankReporter` — 每 Activity/Window 一个实例,用 `PerformanceMetricsState` 给帧数据打屏幕名标签,按屏幕聚合上报。
- `StartupTrackerInitializer` / `AnrCheckInitializer` / `CrashReportingInitializer` / `MemorySamplerInitializer` — 四个 `AppInitializer`(来自 `:core-init`)实现,只用 `javax.inject.Inject` 构造器标注,不用 Hilt 也能手动构造。

## 已知限制 / 不要做的事

- **不要**用 `androidx.startup:startup-runtime` 初始化这里的任何采集器——它自己注册一个 `ContentProvider`,等于往正要测量的 provider 初始化窗口里再塞一个 provider。统一走 `:core-init` 的显式 `Application.onCreate`/`attachBaseContext` 编排。
- **不要**定时采集 `Debug.MemoryInfo`——`getProcessMemoryInfo` 很贵,且 API 29+ 起被系统限流 5 分钟一次;`MemorySampler` 只在冷启动完成/进后台/系统低内存三个时刻采集昂贵档,其余时间只用几乎零开销的 `Runtime.*`。
- **不要**写看门狗线程去检测 ANR——主线程轮询自身本身就有性能开销,且自己可能成为 ANR 的一部分。`ForegroundExitBeacon` 是零运行时开销的启发式方案,聚合统计交给 Play Vitals。
- **不要**在 `FrameReport` 里暴露 `androidx.metrics` 的 `FrameData`——那会让所有 `Telemetry` 实现方被迫依赖 JankStats;`FrameReport` 只包含聚合后的原始类型字段。
- `SamplingTelemetry.crash()` 永远无条件转发,不受采样影响——采样掉崩溃报告等于系统性低估崩溃率。
