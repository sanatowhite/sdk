# :debug-tools

## 这是什么 / 不是什么

应用内 Debug Drawer:可拖拽把手 + 抽屉,内容是本地 feature flag 覆写 + 崩溃/ANR/OOM 一键触发 + 启动/卡顿/网络日志(读 `:core-telemetry` 的 `RingLogBuffer`)。只在 `debugImplementation` 下参与构建。

**不是**:不是一个可独立发布的库(不写 README 的"独立引入"章节里那种"改个坐标就能用"——它专门为本模板的 `:app` debug 门面设计)。不含完整的远程配置系统(`RemoteJsonSource`/`FlagKey`/`AppFlags` 注册表是明显更大的一块能力,这里只做本地覆写,演示交互模式)。

## 独立引入

不建议独立引入——这个模块的存在前提是 `:app` 提供 `app/src/{debug,release}` 的 `DebugOverlay` 门面(debug 门面调用这里的 `DebugDrawer`,release 门面是内联透传,同名函数,`:app` 其余代码不需要关心自己是哪个 buildType)。想要类似能力,参考 `DebugDrawer.kt`/`DebugDrawerContent.kt` 自己接线。

零残留由 Gradle 的 `debugImplementation` 配置本身保证——这个模块的 class 文件结构性地不会出现在 release 的编译/运行时 classpath 上,不是"代码路径没走到"那种脆弱保证。CI 应该再加一道 `apkanalyzer dex packages app-release.apk | grep debugtools`(本地已验证 release APK 干净)。

## 公开 API

- `DebugDrawer(ringLogBuffer, content)` — 顶层入口,包一层 `ModalNavigationDrawer(gesturesEnabled = false)` + 可拖拽把手。
- `CrashTriggers` — `triggerCrash()`/`triggerAnr()`(阻塞主线程 15s)/`triggerOom()`。
- `DebugFlagStore(context)` — SharedPreferences 支持的本地 flag 覆写,`KNOWN_FLAGS` 列出当前演示用的两个 key。

## 已知限制 / 不要做的事

- **不要**指望这里的 feature flag 是远程可控的——目前只是本地 SharedPreferences 覆写,纯演示用途。真正的远程配置系统需要单独设计(`core-net` 已有的 `HttpClientFactory`/`safeApiCall` 可以直接复用来抓远程 JSON,但 flag 注册表/类型安全这部分本模板没有实现)。
- **不要**在这里触发的日志面板上假设它会自动刷新——`RingLogBuffer` 是持续写入的可变容器,不是 Flow,面板故意做成手动 Refresh 按钮而不是伪装成实时更新。
- **不要**给 `CrashTriggers.triggerAnr()` 之外的其他触发方式加看门狗式的自动恢复逻辑——这几个按钮存在的目的就是让崩溃/ANR/OOM 真的发生,用来验证 `:core-telemetry` 的采集链路,不是"安全地模拟"。
