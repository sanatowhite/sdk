# :debug-tools

## 这是什么 / 不是什么

应用内 Debug Drawer:可拖拽把手 + 抽屉,内容是本地 feature flag 覆写 + 崩溃/ANR/OOM 一键触发 + 启动/卡顿/网络日志(读 `:core-telemetry` 的 `RingLogBuffer`)。设计上只在 `debugImplementation` 下参与构建。

**不是**:不含完整的远程配置系统(`RemoteJsonSource`/`FlagKey`/`AppFlags` 注册表是明显更大的一块能力,这里只做本地覆写,演示交互模式)。

## 一行接入

```kotlin
dependencies {
    debugImplementation("com.github.sanatowhite.sdk:debug-tools:1.0.0")
}
```

`DebugDrawer(ringLogBuffer, content)` 对任何用了 `:core-telemetry` 的消费方直接可用——`api()` 已经把 `RingLogBuffer` 所在的 `core-telemetry` 传给消费方。

零残留由 Gradle 的 `debugImplementation` 配置本身保证——这个模块的 class 文件结构性地不会出现在 release 的编译/运行时 classpath 上,不是"代码路径没走到"那种脆弱保证。CI 建议再加一道 `apkanalyzer dex packages app-release.apk | grep debugtools` 做双重确认。

## AI 接入指南(可直接执行)

**要不要用这个模块**:开发期想要一个可拖拽的调试面板(feature flag 覆写、崩溃/ANR/OOM 触发、日志查看)时加。release/staging 构建不应该看到它——必须用 `debugImplementation`,不能用 `implementation`。

**接入步骤**:
1. 加坐标:`debugImplementation("com.github.sanatowhite.sdk:debug-tools:1.0.0")`。
2. 建三个 build-type 专属的 facade 文件(`app/src/{debug,release,staging}/.../DebugOverlay.kt`),提供同一个 `@Composable` 入口签名——debug 版本调用真正的 `DebugDrawer`,release/staging 版本是空函数体的 no-op。
3. 在 `MainActivity` 的 `setContent {}` 里包一层 `DebugOverlay(ringLogBuffer = ringLogBuffer) { /* 你的正常内容 */ }`。

**验证**:`./gradlew :app:assembleRelease` 之后跑 `apkanalyzer dex packages app-release.apk | grep -i debugtools`——**必须无输出**,有输出说明 debug-only 代码泄漏进了 release APK,是需要立刻修的严重问题。

**不要做的事**:见"已知限制"——不要指望这里的 feature flag 是远程可控的;不要给日志面板加自动刷新;不要给崩溃/ANR/OOM 触发按钮加"安全模拟"逻辑。

## 公开 API

- `DebugDrawer(ringLogBuffer, content)` — 顶层入口,包一层 `ModalNavigationDrawer(gesturesEnabled = false)` + 可拖拽把手。
- `CrashTriggers` — `triggerCrash()`/`triggerAnr()`(阻塞主线程 15s)/`triggerOom()`。
- `DebugFlagStore(context)` — SharedPreferences 支持的本地 flag 覆写,`KNOWN_FLAGS` 列出当前演示用的两个 key。

## 已知限制 / 不要做的事

- **不要**指望这里的 feature flag 是远程可控的——目前只是本地 SharedPreferences 覆写,纯演示用途。真正的远程配置系统需要单独设计(`:core-net` 已有的 `HttpClientFactory`/`safeApiCall` 可以直接复用来抓远程 JSON,但 flag 注册表/类型安全这部分本模板没有实现)。
- **不要**在这里触发的日志面板上假设它会自动刷新——`RingLogBuffer` 是持续写入的可变容器,不是 Flow,面板故意做成手动 Refresh 按钮而不是伪装成实时更新。
- **不要**给 `CrashTriggers.triggerAnr()` 之外的其他触发方式加看门狗式的自动恢复逻辑——这几个按钮存在的目的就是让崩溃/ANR/OOM 真的发生,用来验证 `:core-telemetry` 的采集链路,不是"安全地模拟"。
