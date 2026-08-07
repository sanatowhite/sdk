# :debug-tools

## 这是什么 / 不是什么

应用内 Debug Drawer:可拖拽把手 + 抽屉,内容是本地 feature flag 覆写 + 崩溃/ANR/OOM 一键触发 + 启动/卡顿/网络日志(读 `:core-telemetry` 的 `RingLogBuffer`)+ 一个消费方可选挂载的 `extraContent` 插槽(见下面"公开 API")。设计上只在 `debugImplementation` 下参与构建。

**不是**:不含完整的远程配置系统(`RemoteJsonSource`/`FlagKey`/`AppFlags` 注册表是明显更大的一块能力,这里只做本地覆写,演示交互模式)。

## 一行接入

```kotlin
dependencies {
    debugImplementation("com.github.sanatowhite.sdk:debug-tools:1.0.0")
}
```

`DebugDrawer(ringLogBuffer, content)` 对任何用了 `:core-telemetry` 的消费方直接可用——`api()` 已经把 `RingLogBuffer` 所在的 `core-telemetry` 传给消费方。

零残留由 Gradle 的 `debugImplementation` 配置本身保证——这个模块的 class 文件结构性地不会出现在 release 的编译/运行时 classpath 上,不是"代码路径没走到"那种脆弱保证。CI 建议再加一道 `apkanalyzer dex packages app-release.apk | grep debugtools` 做双重确认。

## 公开 API

- `DebugDrawer(ringLogBuffer, extraContent = {}, content)` — 顶层入口,包一层 `ModalNavigationDrawer(gesturesEnabled = false)` + 可拖拽把手。`extraContent` 渲染在抽屉内容的末尾,是消费方自带内容的挂载点——本模块对它一无所知,只负责渲染。
- `CrashTriggers` — `triggerCrash()`/`triggerAnr()`(阻塞主线程 15s)/`triggerOom()`。
- `DebugFlagStore(context)` — SharedPreferences 支持的本地 flag 覆写,`KNOWN_FLAGS` 列出当前演示用的两个 key。

`:app` 用 `extraContent` 挂了一个 LogKit 调试面板(`app/src/debug/.../LogKitDebugPanel.kt`:并发压测按钮、5MB 预算进度条 + 文件列表、崩溃/ANR 触发、`Export & share`)——这个面板**不在本模块里**,因为 `:logkit` 不是本模块的依赖:`:debug-tools` 已发布(见上面"一行接入"),`:logkit` 没有(见 CLAUDE.md ":logkit 五条铁律" #1),已发布模块依赖未发布模块会被 `verifyModuleGraph` 的发布正确性检查拦下来。想要类似能力,参考 `LogKitDebugPanel.kt` 自己接线,传进 `extraContent`。

## 已知限制 / 不要做的事

- **不要**指望这里的 feature flag 是远程可控的——目前只是本地 SharedPreferences 覆写,纯演示用途。真正的远程配置系统需要单独设计(`:core-net` 已有的 `HttpClientFactory`/`safeApiCall` 可以直接复用来抓远程 JSON,但 flag 注册表/类型安全这部分本模板没有实现)。
- **不要**在这里触发的日志面板上假设它会自动刷新——`RingLogBuffer`/`LogKit.stats()` 都是拍快照,不是 Flow,面板故意做成手动 Refresh 按钮而不是伪装成实时更新。
- **不要**给 `:debug-tools` 加 `:logkit` 依赖来"简化"LogKitDebugPanel 的接线——这会让一个已发布模块依赖一个未发布模块,见上面"公开 API"一节;正确的方向永远是消费方通过 `extraContent` 往里挂内容,不是反过来。
- **不要**指望 `extraContent` 里挂的 LogKit 面板能自己验证"顺序一致性"——记录已加密,进程内没有私钥,面板只报告写入/丢弃/耗时,连续性必须离线跑 `logkit-decrypt --verify-seq` 验证。
- **不要**给 `CrashTriggers.triggerAnr()` 之外的其他触发方式加看门狗式的自动恢复逻辑——这几个按钮存在的目的就是让崩溃/ANR/OOM 真的发生,用来验证 `:core-telemetry` 的采集链路,不是"安全地模拟"。
